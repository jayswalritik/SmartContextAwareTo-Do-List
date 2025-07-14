package com.example.smartto_do_list;

import com.example.smartto_do_list.NotificationLog;

public interface NotificationListItem {
    int TYPE_HEADER = 0;
    int TYPE_NOTIFICATION = 1;

    int getType();

    class HeaderItem implements NotificationListItem {
        private final String headerTitle;

        public HeaderItem(String headerTitle) {
            this.headerTitle = headerTitle;
        }

        public String getHeaderTitle() {
            return headerTitle;
        }

        @Override
        public int getType() {
            return TYPE_HEADER;
        }
    }

    class NotificationItem implements NotificationListItem {
        private final NotificationLog notification;

        public NotificationItem(NotificationLog notification) {
            this.notification = notification;
        }

        public NotificationLog getNotification() {
            return notification;
        }

        @Override
        public int getType() {
            return TYPE_NOTIFICATION;
        }
    }
}
