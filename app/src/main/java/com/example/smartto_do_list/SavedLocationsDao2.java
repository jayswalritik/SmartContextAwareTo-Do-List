package com.example.smartto_do_list;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SavedLocationsDao2 {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(SavedLocations location);

    @Query("SELECT * FROM saved_locations")
    List<SavedLocations> getAllSavedLocations();

    @Query("SELECT * FROM saved_locations WHERE LOWER(label) = LOWER(:label) LIMIT 1")
    SavedLocations getLocationByLabel(String label);
    @Query("SELECT * FROM saved_locations WHERE label LIKE 'Unnamed Location %'" )
    List<SavedLocations> getLocationsWithUnnamedLabel();
    @Query("SELECT * FROM saved_locations WHERE id = :locationId LIMIT 1")
    SavedLocations getLocationById(int locationId);

    @Update
    void update(SavedLocations savedLocations);

    @Delete
    void delete(SavedLocations location);

}
