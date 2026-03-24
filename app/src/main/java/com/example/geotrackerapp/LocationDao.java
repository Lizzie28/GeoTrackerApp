package com.example.geotrackerapp;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface LocationDao {

    @Insert
    void insert(LocationEntity location);

    @Delete
    void delete(LocationEntity location);

    @Update
    void update(LocationEntity location);

    @Query("DELETE FROM locations WHERE name = :name")
    void deleteByName(String name);

    @Query("SELECT * FROM locations")
    List<LocationEntity> getAllLocations();
}