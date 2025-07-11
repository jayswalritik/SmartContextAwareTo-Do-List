package com.example.smartto_do_list;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Task.class, SavedLocations.class, Note.class}, version = 1)
public abstract class TaskDatabase2 extends RoomDatabase {

    private static TaskDatabase instance;

    public abstract TaskDao taskDao();
    public abstract SavedLocationsDao savedLocationDao();
    public abstract NoteDao noteDao();

    public static synchronized TaskDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                            TaskDatabase.class, "task_database")
                    .fallbackToDestructiveMigration() // wipe DB on upgrade
                    .allowMainThreadQueries()
                    .build();
        }
        return instance;
    }
}
