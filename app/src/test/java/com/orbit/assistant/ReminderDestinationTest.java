package com.orbit.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Choose your reminder app: one destination per request, Samsung Reminder only through the public
 * entry points it declares, never a claimed success Orbit cannot see, and never two copies.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {29, 35})
public class ReminderDestinationTest {
    private Application context;
    private TimeZone originalZone;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        Prefs.get(context).edit().clear().commit();
        context.getSharedPreferences("orbit_reminders_v1", 0).edit().clear().commit();
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        OrbitNotificationChannels.ensureAll(context);
        originalZone = TimeZone.getDefault();
    }

    @After public void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private void installSamsung(Intent declared) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = ReminderDestination.SAMSUNG_PACKAGE;
        info.activityInfo.name = ReminderDestination.SAMSUNG_PACKAGE + ".EditActivity";
        shadowOf(context.getPackageManager()).addResolveInfoForIntent(declared, info);
    }

    private static AssistantReply.Action reminder(String extra) throws Exception {
        Calendar tomorrow = Calendar.getInstance();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);
        JSONObject p = new JSONObject()
                .put("message", "Take out the trash")
                .put("year", tomorrow.get(Calendar.YEAR))
                .put("month", tomorrow.get(Calendar.MONTH) + 1)
                .put("day", tomorrow.get(Calendar.DAY_OF_MONTH))
                .put("hour", 20).put("minute", 0);
        if (extra != null) {
            JSONObject more = new JSONObject(extra);
            for (java.util.Iterator<String> k = more.keys(); k.hasNext(); ) {
                String key = k.next();
                p.put(key, more.get(key));
            }
        }
        return new AssistantReply.Action("SET_REMINDER", p, false);
    }

    // ---- the default ---------------------------------------------------------------------------

    @Test public void anUpdatingUserKeepsOrbitRemindersEvenWithSamsungAvailable() {
        installSamsung(ReminderDestination.fullIntent("", 0L));
        ReminderDestination.settleForInstall(context, true);
        assertEquals(ReminderDestination.ORBIT, ReminderDestination.current(context));
    }

    @Test public void aFreshInstallStartsOnSamsungOnlyWithTheFullIntegration() {
        installSamsung(ReminderDestination.fullIntent("", 0L));
        ReminderDestination.settleForInstall(context, false);
        assertEquals(ReminderDestination.SAMSUNG, ReminderDestination.current(context));
    }

    @Test public void aFreshInstallWithTextOnlySamsungStartsOnOrbit() {
        installSamsung(ReminderDestination.shareIntent(""));
        assertEquals(ReminderDestination.SHARE, ReminderDestination.samsungSupport(context));
        ReminderDestination.settleForInstall(context, false);
        assertEquals(ReminderDestination.ORBIT, ReminderDestination.current(context));
    }

    @Test public void settlingRunsOnceAndNeverOverwritesAChoice() {
        ReminderDestination.set(context, ReminderDestination.SAMSUNG);
        ReminderDestination.settleForInstall(context, true);
        assertEquals(ReminderDestination.SAMSUNG, ReminderDestination.current(context));
        ReminderDestination.set(context, ReminderDestination.ORBIT);
        installSamsung(ReminderDestination.fullIntent("", 0L));
        ReminderDestination.settleForInstall(context, false);
        assertEquals(ReminderDestination.ORBIT, ReminderDestination.current(context));
    }

    @Test public void theChoiceTravelsWithABackupButTheInstallFlagDoesNot() throws Exception {
        ReminderDestination.set(context, ReminderDestination.SAMSUNG);
        ReminderDestination.settleForInstall(context, true);
        JSONObject backup = Prefs.backupSnapshot(context);
        assertEquals(ReminderDestination.SAMSUNG, backup.optString(Prefs.REMINDER_APP));
        assertFalse(backup.toString().contains(ReminderDestination.SETTLED));
    }

    // ---- Samsung Reminder ----------------------------------------------------------------------

    @Test public void samsungOpensWithTitleAndTimeAndIsNeverCalledCreated() throws Exception {
        installSamsung(ReminderDestination.fullIntent("", 0L));
        ReminderDestination.set(context, ReminderDestination.SAMSUNG);
        AssistantReply.Action action = reminder(null);
        DeviceActionExecutor.Result result = DeviceActionExecutor.executeDetailed(context, action);

        assertEquals(DeviceActionExecutor.STATUS_HANDED_OFF, result.status);
        assertTrue(result.message.contains("Tap Save"));
        assertFalse(result.message.toLowerCase().contains("created"));
        Intent opened = shadowOf(context).getNextStartedActivity();
        assertNotNull(opened);
        assertEquals(ReminderDestination.ACTION_CREATE_REMINDER, opened.getAction());
        assertEquals(ReminderDestination.SAMSUNG_PACKAGE, opened.getPackage());
        assertEquals("Take out the trash", opened.getStringExtra(Intent.EXTRA_TITLE));
        assertTrue(opened.getLongExtra(ReminderDestination.EXTRA_TIME, 0L) > System.currentTimeMillis());
        assertTrue("nothing is also created in Orbit", ReminderStore.list(context).isEmpty());
        assertTrue(ReminderDestination.isSamsungReminder(action));
    }

    @Test public void textOnlySamsungSaysWhichTimeToSet() throws Exception {
        installSamsung(ReminderDestination.shareIntent(""));
        DeviceActionExecutor.Result result = DeviceActionExecutor.executeDetailed(context,
                reminder("{\"destination\":\"samsung\"}"));
        assertEquals(DeviceActionExecutor.STATUS_HANDED_OFF, result.status);
        assertTrue(result.message.contains("Set the time to"));
        Intent opened = shadowOf(context).getNextStartedActivity();
        assertEquals(Intent.ACTION_SEND, opened.getAction());
        assertEquals("Take out the trash", opened.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test public void aRepeatSamsungCannotReceiveIsStatedNotDropped() throws Exception {
        installSamsung(ReminderDestination.fullIntent("", 0L));
        DeviceActionExecutor.Result result = DeviceActionExecutor.executeDetailed(context,
                reminder("{\"destination\":\"samsung\",\"repeat\":\"every 2 weeks\"}"));
        assertEquals(DeviceActionExecutor.STATUS_HANDED_OFF, result.status);
        assertTrue(result.message.contains("repeat every 2 weeks"));
    }

    @Test public void unavailableSamsungCreatesNothingAndOffersOrbitOnce() throws Exception {
        ReminderDestination.set(context, ReminderDestination.SAMSUNG);
        AssistantReply.Action action = reminder(null);
        DeviceActionExecutor.Result result = DeviceActionExecutor.executeDetailed(context, action);

        assertEquals(DeviceActionExecutor.STATUS_UNAVAILABLE, result.status);
        assertNull(shadowOf(context).getNextStartedActivity());
        assertTrue(ReminderStore.list(context).isEmpty());
        assertTrue(ReminderDestination.offersOrbitFallback(action, result.status));

        AssistantReply.Action orbit = ReminderDestination.orbitFallback(action);
        DeviceActionExecutor.Result fallback = DeviceActionExecutor.executeDetailed(context, orbit);
        assertEquals(DeviceActionExecutor.STATUS_SUCCESS, fallback.status);
        assertEquals(1, ReminderStore.list(context).size());
        assertFalse("a delivered Orbit reminder offers no second fallback",
                ReminderDestination.offersOrbitFallback(orbit, fallback.status));
    }

    @Test public void aSecondStepNeverBuriesTheSamsungScreenItIsWaitingOn() throws Exception {
        installSamsung(ReminderDestination.fullIntent("", 0L));
        ReminderDestination.set(context, ReminderDestination.SAMSUNG);
        java.util.List<DeviceActionExecutor.Result> steps = new java.util.ArrayList<>();
        boolean[] completedAll = {true};
        OrbitActionEngine.execute(context, java.util.Arrays.asList(reminder(null), reminder(null)),
                null, new OrbitActionEngine.Listener() {
                    @Override public void onStep(AssistantReply.Action a, DeviceActionExecutor.Result r,
                                                 int index, int total) { steps.add(r); }
                    @Override public void onFinished(boolean all, int done, int total) {
                        completedAll[0] = all;
                    }
                });
        assertEquals(2, steps.size());
        assertEquals(DeviceActionExecutor.STATUS_HANDED_OFF, steps.get(0).status);
        assertEquals(DeviceActionExecutor.STATUS_CANCELLED, steps.get(1).status);
        assertTrue(steps.get(1).message.startsWith("Not run"));
        assertFalse(completedAll[0]);
        assertNotNull(shadowOf(context).getNextStartedActivity());
        assertNull("only one Samsung screen was opened", shadowOf(context).getNextStartedActivity());
    }

    // ---- explicit override and Orbit Reminders -------------------------------------------------

    @Test public void anExplicitOrbitRequestBeatsASamsungDefault() throws Exception {
        installSamsung(ReminderDestination.fullIntent("", 0L));
        ReminderDestination.set(context, ReminderDestination.SAMSUNG);
        DeviceActionExecutor.Result result = DeviceActionExecutor.executeDetailed(context,
                reminder("{\"destination\":\"orbit\"}"));
        assertEquals(DeviceActionExecutor.STATUS_SUCCESS, result.status);
        assertNull(shadowOf(context).getNextStartedActivity());
        assertEquals(1, ReminderStore.list(context).size());
    }

    @Test public void anExplicitSamsungRequestBeatsAnOrbitDefault() throws Exception {
        installSamsung(ReminderDestination.fullIntent("", 0L));
        DeviceActionExecutor.Result result = DeviceActionExecutor.executeDetailed(context,
                reminder("{\"destination\":\"Samsung Reminder\"}"));
        assertEquals(DeviceActionExecutor.STATUS_HANDED_OFF, result.status);
        assertTrue(ReminderStore.list(context).isEmpty());
    }

    @Test public void orbitRemindersRepeatWeeklyAndRefuseOtherPatterns() throws Exception {
        DeviceActionExecutor.Result weekly = DeviceActionExecutor.executeDetailed(context,
                reminder("{\"repeat\":\"weekly\"}"));
        assertEquals(DeviceActionExecutor.STATUS_SUCCESS, weekly.status);
        assertTrue(weekly.message.contains("repeating weekly"));
        assertEquals(ReminderStore.REPEAT_WEEKLY, ReminderStore.list(context).get(0).repeat);

        DeviceActionExecutor.Result monthly = DeviceActionExecutor.executeDetailed(context,
                reminder("{\"repeat\":\"monthly\"}"));
        assertEquals(DeviceActionExecutor.STATUS_FAILED, monthly.status);
        assertEquals("the refused request created nothing", 1, ReminderStore.list(context).size());
    }

    @Test public void aFiredWeeklyReminderBooksNextWeekAndAOneTimeOneIsGone() {
        long soon = System.currentTimeMillis() + 60_000L;
        ReminderStore.Item weekly = ReminderStore.create("Check my schedule", soon, "weekly");
        ReminderStore.Item once = ReminderStore.create("Call Niki", soon);
        ReminderStore.upsert(context, weekly);
        ReminderStore.upsert(context, once);

        fire(weekly.id);
        fire(once.id);

        assertNull(ReminderStore.get(context, once.id));
        ReminderStore.Item next = ReminderStore.get(context, weekly.id);
        assertNotNull(next);
        assertTrue(next.triggerAt > soon + 6L * 24 * 3_600_000L);
    }

    private void fire(String id) {
        new ReminderReceiver().onReceive(context, new Intent()
                .putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id));
    }

    @Test public void weeklyKeepsItsWallClockTimeAcrossDaylightSaving() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        Calendar from = Calendar.getInstance();
        from.clear();
        from.set(2026, Calendar.OCTOBER, 30, 20, 0);
        long next = ReminderStore.nextOccurrence(from.getTimeInMillis(),
                ReminderStore.REPEAT_WEEKLY, from.getTimeInMillis());
        Calendar got = Calendar.getInstance();
        got.setTimeInMillis(next);
        assertEquals(6, got.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.NOVEMBER, got.get(Calendar.MONTH));
        assertEquals(20, got.get(Calendar.HOUR_OF_DAY));
    }

    @Test public void oneTimeRemindersKeepTheirStoredShapeAndRepeatSurvivesARoundTrip() throws Exception {
        assertFalse(ReminderStore.create("Once", 5_000L).toJson().has("repeat"));
        ReminderStore.Item daily = ReminderStore.Item.fromJson(
                ReminderStore.create("Water plants", 5_000L, "daily").toJson());
        assertEquals(ReminderStore.REPEAT_DAILY, daily.repeat);
        ReminderStore.Item legacy = ReminderStore.Item.fromJson(new JSONObject(
                "{\"id\":\"a\",\"message\":\"Old\",\"triggerAt\":5000,\"createdAt\":1}"));
        assertEquals("", legacy.repeat);
    }
}
