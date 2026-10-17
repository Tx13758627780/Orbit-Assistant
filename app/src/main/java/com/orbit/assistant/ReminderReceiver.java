package com.orbit.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires a stored Orbit reminder, and books the next time of a repeating one. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String EXTRA_REMINDER_ID = "reminder_id";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String id = intent.getStringExtra(EXTRA_REMINDER_ID);
        ReminderStore.Item item = ReminderStore.get(context, id);
        if (item == null) return;
        ReminderStore.Item next = item.next(System.currentTimeMillis());
        if (next == null || !ReminderScheduler.schedule(context, next).scheduled) {
            ReminderStore.remove(context, item.id);
        }
        ReminderNotifier.show(context, item);
    }
}
