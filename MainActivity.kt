package com.example.autocallalarm

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

// ---------------------------------------------------------------------------
// Everything for this app lives in this one file: the screen you interact
// with (MainActivity), the piece that fires at the scheduled time
// (AlarmReceiver), and the piece that re-arms the schedule after a reboot
// (BootReceiver). They're still separate classes because Android requires
// a real class name in the manifest for each receiver, but there's only one
// file to read.
// ---------------------------------------------------------------------------

private const val PREFS = "auto_call_alarm_prefs"

class MainActivity : Activity() {

    private lateinit var phoneInput: EditText
    private lateinit var timePicker: TimePicker
    private lateinit var alarmCheckbox: CheckBox
    private lateinit var statusText: TextView
    private val callPermissionRequestCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        timePicker = TimePicker(this).apply { setIs24HourView(true) }
        phoneInput = EditText(this).apply {
            hint = "Phone number, e.g. +911234567890"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("phone_number", ""))
        }
        alarmCheckbox = CheckBox(this).apply {
            text = "Also open the Clock app with an alarm set"
            isChecked = prefs.getBoolean("also_set_alarm", true)
        }
        statusText = TextView(this)

        val grantButton = Button(this).apply { text = "1. Grant call permission" }
        val scheduleButton = Button(this).apply { text = "2. Schedule" }

        grantButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CALL_PHONE), callPermissionRequestCode
                )
            } else {
                Toast.makeText(this, "Call permission already granted.", Toast.LENGTH_SHORT).show()
            }
        }

        scheduleButton.setOnClickListener {
            val number = phoneInput.text.toString().trim()
            if (number.isEmpty()) {
                Toast.makeText(this, "Enter a phone number first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val hour = timePicker.hour
            val minute = timePicker.minute

            prefs.edit()
                .putString("phone_number", number)
                .putInt("hour", hour)
                .putInt("minute", minute)
                .putBoolean("also_set_alarm", alarmCheckbox.isChecked)
                .putBoolean("scheduled", true)
                .apply()

            scheduleDailyTrigger(this, hour, minute)
            Toast.makeText(this, "Scheduled for %02d:%02d daily.".format(hour, minute), Toast.LENGTH_LONG).show()
            refreshStatus()
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            addView(timePicker)
            addView(phoneInput)
            addView(alarmCheckbox)
            addView(grantButton)
            addView(scheduleButton)
            addView(statusText)
        }
        setContentView(root)
        refreshStatus()
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val hour = prefs.getInt("hour", -1)
        val minute = prefs.getInt("minute", -1)
        statusText.text = buildString {
            append(if (hasPermission) "✓ Call permission granted.\n" else "✗ No call permission (will open dialer instead).\n")
            append(if (hour >= 0) "Scheduled daily at %02d:%02d.".format(hour, minute) else "Not scheduled yet.")
        }
    }

    companion object {
        /** Schedules (or re-schedules) the daily trigger. Shared by MainActivity, AlarmReceiver, and BootReceiver. */
        fun scheduleDailyTrigger(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val trigger = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.timeInMillis, pendingIntent)
        }
    }
}

/** Fires at the scheduled time: opens the Clock app (optional) and dials the saved number. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val number = prefs.getString("phone_number", null)
        val hour = prefs.getInt("hour", -1)
        val minute = prefs.getInt("minute", -1)

        if (prefs.getBoolean("also_set_alarm", true)) {
            context.startActivity(
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Auto Call Alarm")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        }

        if (!number.isNullOrBlank()) {
            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED
            context.startActivity(
                Intent(if (hasPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        if (hour >= 0 && minute >= 0) {
            MainActivity.scheduleDailyTrigger(context, hour, minute) // re-arm for tomorrow
        }
    }
}

/** Android clears all AlarmManager alarms on reboot; this restores the schedule automatically. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("scheduled", false)) return
        val hour = prefs.getInt("hour", -1)
        val minute = prefs.getInt("minute", -1)
        if (hour >= 0 && minute >= 0) {
            MainActivity.scheduleDailyTrigger(context, hour, minute)
        }
    }
}
