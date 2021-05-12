package com.anshmidt.multialarm.activities

import androidx.appcompat.app.AppCompatActivity
import com.anshmidt.multialarm.dialogs.IntervalDialogFragment.IntervalDialogListener
import com.anshmidt.multialarm.dialogs.NumberOfAlarmsDialogFragment.NumberOfAlarmsDialogListener
import android.app.TimePickerDialog.OnTimeSetListener
import androidx.appcompat.widget.SwitchCompat
import com.anshmidt.multialarm.view_helpers.AlarmsListHelper
import com.anshmidt.multialarm.SharedPreferencesHelper
import com.anshmidt.multialarm.TimerManager
import com.anshmidt.multialarm.AlarmParams
import android.content.BroadcastReceiver
import com.anshmidt.multialarm.activities.MainActivity
import android.os.Bundle
import com.anshmidt.multialarm.R
import com.anshmidt.multialarm.dialogs.IntervalDialogFragment
import com.anshmidt.multialarm.dialogs.NumberOfAlarmsDialogFragment
import com.anshmidt.multialarm.dialogs.TimePickerDialogFragment
import android.content.Intent
import android.content.IntentFilter
import com.anshmidt.multialarm.activities.PrefActivity
import com.anshmidt.multialarm.AlarmTime
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*

class MainActivity : AppCompatActivity(), IntervalDialogListener, NumberOfAlarmsDialogListener, OnTimeSetListener {
    var onOffSwitch: SwitchCompat? = null
    var alarmsListView: ListView? = null
    var intervalBetweenAlarmsTextView: TextView? = null
    var numberOfAlarmsTextView: TextView? = null
    var firstAlarmTextView: TextView? = null
    var timeLeftTextView: TextView? = null
    var firstAlarmLayout: LinearLayout? = null
    var intervalLayout: LinearLayout? = null
    var numberOfAlarmsLayout: LinearLayout? = null
    var alarmsListHelper: AlarmsListHelper? = null
    var sharPrefHelper: SharedPreferencesHelper? = null
    var timerManager: TimerManager? = null
    var alarmParams: AlarmParams? = null
    var timeLeftReceiver: BroadcastReceiver? = null
    private val LOG_TAG = MainActivity::class.java.simpleName
    val ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 45
    val DISPLAYED_NUMBERS_SIZE_RELATIVE_TO_TEXT_PROPORTION = 2f // number of alarms, first alarm, interval values text size is larger than text around them
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        alarmsListView = findViewById<View>(R.id.listview_main_alarmslist) as ListView
        onOffSwitch = findViewById<View>(R.id.switch_main) as SwitchCompat
        intervalBetweenAlarmsTextView = findViewById<View>(R.id.textview_main_interval) as TextView
        numberOfAlarmsTextView = findViewById<View>(R.id.textview_main_numberofalarms) as TextView
        firstAlarmLayout = findViewById<View>(R.id.layout_main_firstalarm) as LinearLayout
        firstAlarmTextView = findViewById<View>(R.id.textview_main_firstalarm_time) as TextView
        timeLeftTextView = findViewById<View>(R.id.textview_main_timeleft) as TextView
        intervalLayout = findViewById<View>(R.id.layout_main_interval) as LinearLayout
        numberOfAlarmsLayout = findViewById<View>(R.id.layout_main_numberofalarms) as LinearLayout
        sharPrefHelper = SharedPreferencesHelper(this@MainActivity)
        sharPrefHelper!!.printAll()
        alarmParams = sharPrefHelper!!.params
        timerManager = TimerManager(this@MainActivity)
        alarmsListHelper = AlarmsListHelper(this@MainActivity, alarmsListView)
        showFirstAlarmTime(alarmParams!!.firstAlarmTime.toString())
        showTimeLeft(alarmParams)
        showInterval(sharPrefHelper!!.intervalStr)
        showNumberOfAlarms(sharPrefHelper!!.numberOfAlarmsStr)
        onOffSwitch!!.isChecked = sharPrefHelper!!.isAlarmTurnedOn
        alarmsListHelper!!.showList(alarmParams)
        onOffSwitch!!.setOnCheckedChangeListener { buttonView, isChecked ->
            alarmParams!!.turnedOn = isChecked
            if (isChecked) {
                checkNotificationPolicy()
                checkOverlayPermission()
                timerManager!!.startSingleAlarmTimer(alarmParams!!.firstAlarmTime.toMillis())
                showToast(getString(R.string.main_alarm_turned_on_toast))
                sharPrefHelper!!.numberOfAlreadyRangAlarms = 0
            } else {
                timerManager!!.cancelTimer()
                showToast(getString(R.string.main_alarm_turned_off_toast))
            }
            alarmsListHelper!!.showList(alarmParams)
            showTimeLeft(alarmParams)
            sharPrefHelper!!.setAlarmState(isChecked)
        }
        intervalLayout!!.setOnClickListener {
            val dialog = IntervalDialogFragment()
            val intervalBundle = Bundle()
            intervalBundle.putString(IntervalDialogFragment.BUNDLE_KEY_INTERVAL, sharPrefHelper!!.intervalStr)
            dialog.arguments = intervalBundle
            dialog.show(fragmentManager, IntervalDialogFragment.FRAGMENT_TAG)
        }
        numberOfAlarmsLayout!!.setOnClickListener {
            val dialog = NumberOfAlarmsDialogFragment()
            val numberOfAlarmsBundle = Bundle()
            numberOfAlarmsBundle.putString(NumberOfAlarmsDialogFragment.BUNDLE_KEY_NUMBER_OF_ALARMS, sharPrefHelper!!.numberOfAlarmsStr)
            dialog.arguments = numberOfAlarmsBundle
            dialog.show(fragmentManager, NumberOfAlarmsDialogFragment.FRAGMENT_TAG)
        }
        firstAlarmLayout!!.setOnClickListener {
            val timePickerBundle = Bundle()
            timePickerBundle.putInt(TimePickerDialogFragment.BUNDLE_KEY_ALARM_HOUR, sharPrefHelper!!.hour)
            timePickerBundle.putInt(TimePickerDialogFragment.BUNDLE_KEY_ALARM_MINUTE, sharPrefHelper!!.minute)
            val timePicker = TimePickerDialogFragment()
            timePicker.arguments = timePickerBundle
            timePicker.show(fragmentManager, TimePickerDialogFragment.FRAGMENT_TAG)
        }
    }

    override fun onResume() {
        super.onResume()
        showTimeLeft(alarmParams)
        timeLeftReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action.compareTo(Intent.ACTION_TIME_TICK) == 0) {  //i.e. every minute
                    showTimeLeft(alarmParams)
                }
            }
        }
        registerReceiver(timeLeftReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }

    override fun onPause() {
        super.onPause()
        if (timeLeftReceiver != null) {
            unregisterReceiver(timeLeftReceiver)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.action_settings -> {
                val intent = Intent(this, PrefActivity::class.java)
                startActivity(intent)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onIntervalChanged(intervalStr: String) {
        showInterval(intervalStr)
        alarmParams!!.interval = intervalStr.toInt()
        alarmsListHelper!!.showList(alarmParams)
        resetTimerIfTurnedOn()
        sharPrefHelper!!.setInterval(intervalStr)
    }

    override fun onNumberOfAlarmsChanged(numberOfAlarmsStr: String) {
        showNumberOfAlarms(numberOfAlarmsStr)
        alarmParams!!.numberOfAlarms = numberOfAlarmsStr.toInt()
        alarmsListHelper!!.showList(alarmParams)
        resetTimerIfTurnedOn()
        sharPrefHelper!!.setNumberOfAlarms(numberOfAlarmsStr)
    }

    override fun onTimeSet(view: TimePicker, hour: Int, minute: Int) {
        val alarmTime = AlarmTime(hour, minute)
        alarmParams!!.firstAlarmTime = alarmTime
        showFirstAlarmTime(alarmTime.toString())
        alarmsListHelper!!.showList(alarmParams)
        showTimeLeft(alarmParams)
        sharPrefHelper!!.numberOfAlreadyRangAlarms = 0
        resetTimerIfTurnedOn()
        sharPrefHelper!!.time = alarmTime
    }

    private fun showToast(message: String) {
        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
    }

    private fun resetTimerIfTurnedOn() {
        if (onOffSwitch!!.isChecked) {
            timerManager!!.resetSingleAlarmTimer(alarmParams!!.firstAlarmTime.toMillis())
            showToast(getString(R.string.main_alarm_reset_toast))
        }
    }

    private fun showInterval(interval: String) {
        val wholeTitle = getString(R.string.main_interval, interval)
        val wholeTitleSpan = SpannableString(wholeTitle)
        wholeTitleSpan.setSpan(RelativeSizeSpan(DISPLAYED_NUMBERS_SIZE_RELATIVE_TO_TEXT_PROPORTION), wholeTitle.indexOf(interval), interval.length + 1, 0)
        intervalBetweenAlarmsTextView!!.text = wholeTitleSpan
    }

    private fun showNumberOfAlarms(numberOfAlarms: String) {
        val numberOfAlarmsInt = numberOfAlarms.toInt()
        val wholeTitle = this.resources.getQuantityString(R.plurals.main_number_of_alarms, numberOfAlarmsInt, numberOfAlarmsInt)
        val wholeTitleSpan = SpannableString(wholeTitle)
        wholeTitleSpan.setSpan(RelativeSizeSpan(DISPLAYED_NUMBERS_SIZE_RELATIVE_TO_TEXT_PROPORTION),
                wholeTitle.indexOf(numberOfAlarms),
                numberOfAlarms.length + 1, 0)
        numberOfAlarmsTextView!!.text = wholeTitleSpan
    }

    private fun showFirstAlarmTime(firstAlarmTime: String) {
        val wholeTitle = getString(R.string.main_firstalarm_time, firstAlarmTime)
        val wholeTitleSpan = SpannableString(wholeTitle)
        wholeTitleSpan.setSpan(RelativeSizeSpan(DISPLAYED_NUMBERS_SIZE_RELATIVE_TO_TEXT_PROPORTION),
                wholeTitle.indexOf(firstAlarmTime) - 1,
                wholeTitle.indexOf(firstAlarmTime) + firstAlarmTime.length, 0)
        firstAlarmTextView!!.text = wholeTitleSpan
    }

    private fun showTimeLeft(alarmParams: AlarmParams?) {
        val alarmTime = alarmParams!!.firstAlarmTime
        timeLeftTextView!!.text = getString(R.string.all_time_left, alarmTime.hoursLeft, alarmTime.minutesLeft)
        if (alarmParams.turnedOn) {
            timeLeftTextView!!.setTextColor(getColor(R.color.primary))
        } else {
            timeLeftTextView!!.setTextColor(getColor(R.color.main_disabled_textcolor))
        }
        Log.d(LOG_TAG, "Time left: " + alarmTime.hoursLeft + ":" + alarmTime.minutesLeft)
    }

    private fun checkNotificationPolicy() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !notificationManager.isNotificationPolicyAccessGranted) {
            val intent = Intent(
                    Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    /**
     * needed for Android Q: on some devices activity doesn't show from fullScreenNotification without
     * permission SYSTEM_ALERT_WINDOW
     */
    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + this.packageName))
            startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }
}