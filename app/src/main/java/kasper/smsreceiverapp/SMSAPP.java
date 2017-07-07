package kasper.smsreceiverapp;

import android.Manifest;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.database.Cursor;
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
import java.util.ArrayList;

import java.util.Calendar;

import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class SMSAPP extends BroadcastReceiver {

    final String wanted_number = "+3584573950113";
    final String own_number = "+358400376167";
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
                Boolean is_wanted_number = number.replaceAll("\\s","").equals(wanted_number);
                Boolean is_own_number = number.replaceAll("\\s","").equals(own_number);
                if (m.find() && (is_wanted_number || is_own_number)) {

                    try {
                        long[] parsed_time = parseTime(body);

                        ArrayList<long[]> forbiddenDates = Utility.readCalendarEvent(context);
                        Boolean is_forbidden = false;
                        for (int j = 0; j < forbiddenDates.size(); j++) {

                            long calendar_start_date = forbiddenDates.get(j)[0];
                            long calendar_end_date = forbiddenDates.get(j)[1];
                            long job_offer_start_date = parsed_time[0];
                            long job_offer_end_date = parsed_time[1];
                      /*      Log.i("dates_calendar", String.valueOf(calendar_start_date));
                            Log.i("dates_calendar", String.valueOf(calendar_end_date));
                            Log.i("dates_job", String.valueOf(job_offer_start_date));
                            Log.i("dates_job", String.valueOf(job_offer_end_date)); */
                            if((calendar_start_date <= job_offer_end_date) && (calendar_end_date >= job_offer_start_date)){
                                is_forbidden = true;
                            }
                        }

                        if(!is_forbidden) {
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
    private long[] parseTime(String msg) throws ParseException {
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


        }
        Pattern compiled_time = Pattern.compile(time_pattern);

        Matcher time_matcher =  compiled_time.matcher(msg);
        if (time_matcher.find()) {


            start_time_string = time_matcher.group(1);
            end_time_string = time_matcher.group(2);

        }
        if(day.length() == 1){
            day = "0" + day;
        }
        if(month.length() == 1){
            month = "0" + month;
        }

        DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        long start_date = df.parse(String.format("%s/%s/%s %s", day, month, year, start_time_string)).getTime();
        long end_date = df.parse(String.format("%s/%s/%s %s", day, month, year, end_time_string)).getTime();

        long[] timeArray;
        timeArray = new long[2];
        timeArray[0] = start_date;
        timeArray[1] = end_date;

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
            return "";
        }
    }
    private void makeCalendarEntry(Context ctx, String title, String comment, long dtstart, long dtend) {
        Calendar dt = Calendar.getInstance();
        dt.add(Calendar.DATE, 1);

        ContentResolver cr = ctx.getContentResolver();
        ContentValues values = new ContentValues();

        values.put(CalendarContract.Events.DTSTART, String.valueOf(dtstart));
        values.put(CalendarContract.Events.DTEND, String.valueOf(dtend));
        values.put(CalendarContract.Events.TITLE, title + " " + comment);


        TimeZone timeZone = TimeZone.getDefault();
        values.put(CalendarContract.Events.EVENT_TIMEZONE, timeZone.getID());

// Default calendar
        values.put(CalendarContract.Events.CALENDAR_ID, 1);

        values.put(CalendarContract.Events.HAS_ALARM, 1);

// Insert event to calendar

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(ctx, "NO PERMISSIONS!", Toast.LENGTH_SHORT).show();

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
class Utility {

    public static ArrayList<long[]> dates = new ArrayList<long[]>();


    public static ArrayList<long[]> readCalendarEvent(Context context) {
        Cursor cursor = context.getContentResolver()
                .query(
                        Uri.parse("content://com.android.calendar/events"),
                        new String[]{"calendar_id", "title", "description",
                                "dtstart", "dtend", "eventLocation"}, null,
                        null, null);
        cursor.moveToFirst();
        // fetching calendars name
        String CNames[] = new String[cursor.getCount()];


        for (int i = 0; i < CNames.length; i++) {
            if(cursor.getString(1).contains("Työt") || cursor.getString(1).contains("Loma")){
                long[] date_datum;
                date_datum = new long[]{Long.parseLong(cursor.getString(3)), Long.parseLong(cursor.getString(4))};
                dates.add(date_datum);

            }

            CNames[i] = cursor.getString(1);
            cursor.moveToNext();
        }

        return dates;
    }

}