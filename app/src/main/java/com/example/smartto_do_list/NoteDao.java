package com.example.smartto_do_list;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;
import androidx.room.Delete;

@Dao
public interface NoteDao {
    @Insert
    long insert(Note note); // Returns ID so you can link to Task

    @Update
    void update(Note note);

    @Delete
    void delete(Note note);

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    Note getNoteById(int noteId);

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    TaskRelationWithSavedLocationsAndNote getTaskNotes(int taskId);
}
