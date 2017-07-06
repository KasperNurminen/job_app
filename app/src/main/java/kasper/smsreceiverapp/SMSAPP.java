package kasper.smsreceiverapp;

import android.Manifest;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;

import android.support.v4.app.ActivityCompat;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import android.widget.Toast;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class SMSAPP extends BroadcastReceiver {

    String[] unwanted_days = {"21.08.2017","22.08.2017","23.08.2017","24.08.2017","25.08.2017","27.08.2017","28.08.2017","29.08.2017","30.08.2017","31.08.2017"};
    final String wanted_number = "123";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();


        SmsMessage[] msgs = null;
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            msgs = new SmsMessage[pdus.length];
            String number = "";
            String body = "";

            for (int i = 0; i < msgs.length; i++) {
                msgs[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);

                number = msgs[i].getOriginatingAddress();
                body = msgs[i].getMessageBody().toString();

                Log.i("number", number);
                String pattern = "Hyväksy vastaamalla ([A-Z]{4})";
                Pattern r = Pattern.compile(pattern);

                Matcher m = r.matcher(body);

                if (m.find()) {

                    try {
                        String[] parsed_time = parseTime(body);
                        String job_date = parsed_time[2];
                        Log.i("job_dates", job_date);
                        Log.i("job_dates", String.valueOf(Arrays.asList(unwanted_days)));

                        if(!Arrays.asList(unwanted_days).contains(job_date)) {
                            Toast.makeText(context, "Received message from job! Auto-accepting...", Toast.LENGTH_SHORT).show();
                            sendSMS(number, m.group(1));

                            Toast.makeText(context, "Sent message: " + m.group(1), Toast.LENGTH_LONG).show();
                            makeCalendarEntry(context, "Työt", parseLocation(body), parsed_time[0], parsed_time[1]);
                        }
                        else{
                            Toast.makeText(context, "Job entry received, but it was on the ban list. Didn't accept.", Toast.LENGTH_LONG).show();

                        }
                    } catch (Exception e) {
                        Toast.makeText(context,
                                "SMS failed, did you remember to give the app sufficient permissions?",
                                Toast.LENGTH_LONG).show();
                        e.printStackTrace();

                    }


                }

            }
        }

    }
    private String[] parseTime(String msg) throws ParseException {
        String date_pattern = "(\\d{1,2}).(\\d{1,2}).(\\d{1,4})";
        String time_pattern = "(\\d{1,2}:\\d{1,2}) - (\\d{1,2}:\\d{1,2})";
        Pattern compiled_date = Pattern.compile(date_pattern);
        Matcher date_matcher =  compiled_date.matcher(msg);
        String day = "00";
        String month = "00";
        String year = "0000";
        String start_time_string = "00:00";
        String end_time_string = "00:00";
        if (date_matcher.find()) {

            day = date_matcher.group(1);
            month = date_matcher.group(2);
            year = date_matcher.group(3);
            Log.i("PARSING-date", day);
            Log.i("PARSING-date", month);
            Log.i("PARSING-date", year);

        }
        Pattern compiled_time = Pattern.compile(time_pattern);

        Matcher time_matcher =  compiled_time.matcher(msg);
        if (time_matcher.find()) {


            start_time_string = time_matcher.group(1);
            end_time_string = time_matcher.group(2);
            Log.i("PARSING-time", start_time_string);
            Log.i("PARSING-time", end_time_string);

        }
        if(day.length() == 1){
            day = "0" + day;
        }
        if(month.length() == 1){
            month = "0" + month;
        }

        DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        String start_date = String.valueOf(df.parse(String.format("%s/%s/%s %s", day, month, year, start_time_string)).getTime());
        String end_date = String.valueOf(df.parse(String.format("%s/%s/%s %s", day, month, year, end_time_string)).getTime());

        String[] timeArray;
        timeArray = new String[3];
        timeArray[0] = start_date;
        timeArray[1] = end_date;
        timeArray[2] = String.format("%s.%s.%s", day, month, year);
        return timeArray;
    }
    private String parseLocation(String msg){
        String pattern = "\\d{1,2}:\\d{1,2}\\n([A-Za-z ]*),";
        Pattern r = Pattern.compile(pattern);

        Matcher m = r.matcher(msg);
        if (m.find()) {
            return m.group(1);
        }
        else{
            return "No location found";
        }
    }
    private void makeCalendarEntry(Context ctx, String title, String comment, String dtstart, String dtend) {
        Calendar dt = Calendar.getInstance();
        dt.add(Calendar.DATE, 1);

        ContentResolver cr = ctx.getContentResolver();
        ContentValues values = new ContentValues();

        values.put(CalendarContract.Events.DTSTART, dtstart);
        values.put(CalendarContract.Events.DTEND, dtend);
        values.put(CalendarContract.Events.TITLE, title + " " + comment);


        TimeZone timeZone = TimeZone.getDefault();
        values.put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.getID());

// Default calendar
        values.put(CalendarContract.Events.CALENDAR_ID, 1);

        values.put(CalendarContract.Events.HAS_ALARM, 1);

// Insert event to calendar

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(ctx, "NO PERMISSIONS!", Toast.LENGTH_SHORT).show();

            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Uri uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
        Toast.makeText(ctx, "Inserted calendar entry to: " + uri, Toast.LENGTH_SHORT).show();

    }
    private void sendSMS(String phoneNumber, String message) {
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, null, null);
    }
}