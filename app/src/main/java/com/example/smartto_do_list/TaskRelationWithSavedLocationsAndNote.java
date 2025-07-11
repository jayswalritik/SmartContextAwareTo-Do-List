package com.example.smartto_do_list;

import androidx.room.Embedded;
import androidx.room.Relation;

public class TaskRelationWithSavedLocationsAndNote {

    @Embedded
    public Task task;

    @Relation(
            parentColumn = "location_id",
            entityColumn = "id"
    )
    public SavedLocations locations;

    @Relation(
            parentColumn = "note_id",
            entityColumn = "id"
    )
    public Note note;
}
