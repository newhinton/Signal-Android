package org.thoughtcrime.securesms.notifications.v2

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.thoughtcrime.securesms.notifications.DeleteNotificationReceiver
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.notifications.NotificationIds
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Hold all state for notifications for all conversations.
 */
data class NotificationStateV2(val conversations: List<NotificationConversation>, val muteFilteredMessages: List<FilteredMessage>, val profileFilteredMessages: List<FilteredMessage>) {

  val threadCount: Int = conversations.size
  val isEmpty: Boolean = conversations.isEmpty()

  val messageCount: Int by lazy {
    conversations.fold(0) { messageCount, conversation ->
      messageCount + conversation.messageCount
    }
  }

  val notificationItems: List<NotificationItemV2> by lazy {
    conversations.map { it.notificationItems }
      .flatten()
      .sorted()
  }

  val notificationIds: Set<Int> by lazy {
    conversations.map { it.notificationId }
      .toSet()
  }

  val mostRecentNotification: NotificationItemV2?
    get() = notificationItems.lastOrNull()

  val mostRecentSender: Recipient?
    get() = mostRecentNotification?.individualRecipient

  fun getNonVisibleConversation(visibleThread: NotificationThread?): List<NotificationConversation> {
    return conversations.filterNot { it.thread == visibleThread }
  }

  fun getConversation(notificationThread: NotificationThread): NotificationConversation? {
    return conversations.firstOrNull { it.thread == notificationThread }
  }

  fun getDeleteIntent(context: Context): PendingIntent? {
    val ids = LongArray(messageCount)
    val mms = BooleanArray(ids.size)
    val threads: MutableList<NotificationThread> = mutableListOf()

    conversations.forEach { conversation ->
      threads += conversation.thread
      conversation.notificationItems.forEachIndexed { index, notificationItem ->
        ids[index] = notificationItem.id
        mms[index] = notificationItem.isMms
      }
    }

    val intent = Intent(context, DeleteNotificationReceiver::class.java)
      .setAction(DeleteNotificationReceiver.DELETE_NOTIFICATION_ACTION)
      .putExtra(DeleteNotificationReceiver.EXTRA_IDS, ids)
      .putExtra(DeleteNotificationReceiver.EXTRA_MMS, mms)
      .putParcelableArrayListExtra(DeleteNotificationReceiver.EXTRA_THREADS, ArrayList(threads))
      .makeUniqueToPreventMerging()

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
  }

  fun getMarkAsReadIntent(context: Context): PendingIntent? {
    val intent = Intent(context, MarkReadReceiver::class.java).setAction(MarkReadReceiver.CLEAR_ACTION)
      .putParcelableArrayListExtra(MarkReadReceiver.THREADS_EXTRA, ArrayList(conversations.map { it.thread }))
      .putExtra(MarkReadReceiver.NOTIFICATION_ID_EXTRA, NotificationIds.MESSAGE_SUMMARY)
      .makeUniqueToPreventMerging()

    return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
  }

  fun getThreadsWithMostRecentNotificationFromSelf(): Set<NotificationThread> {
    return conversations.filter { it.mostRecentNotification.individualRecipient.isSelf }
      .map { it.thread }
      .toSet()
  }

  data class FilteredMessage(val id: Long, val isMms: Boolean)

  companion object {
    val EMPTY = NotificationStateV2(emptyList(), emptyList(), emptyList())
  }
}
