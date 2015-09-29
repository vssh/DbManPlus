package com.vssh.dbmanplus;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.support.annotation.Nullable;
import android.util.Log;

/**
 * Created by varun on 24.08.15.
 *
 * Helper class for DbManPlus. Extend this for each table in database.
 */
public abstract class DbProto {
    private DbManPlus mDbManager;
    private static final String TAG = "ReactDbLib:DbProto";

    public DbProto(DbManPlus dbManager) {
        this.mDbManager = dbManager;
    }

    /**
     * Get table name
     * @return Table name
     */
    public abstract String getTableName();

    /**
     * Check if values should be inserted into the databse
     * @param values
     * @return true, if it should continue to insert
     */
    public abstract boolean continueInsert(ContentValues values);

    /**
     * Insert into this table
     * @param values
     * @return row ID if successful, else -1
     */
    public long insert(ContentValues values) {
        long result = -1;
        try {
            if(this.continueInsert(values)) {
                result = mDbManager.insert(this.getTableName(), values);
            }
            else {
                throw new SQLException("Values exception: check values to be inserted");
            }
        } catch (SQLException | IllegalArgumentException e){
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Insert multiple rows as one transaction
     * @param values
     * @return number of rows
     */
    public int bulkInsert(ContentValues[] values) {
        int result = -1;
        try {
            result = mDbManager.bulkInsert(this.getTableName(), values);
        } catch (SQLException | IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Uppdate rows in this table
     * @param values
     * @param where
     * @param whereArgs
     * @return number of rows
     */
    public int update(ContentValues values, String where, String[] whereArgs) {
        int result = -1;
        try {
            return mDbManager.update(this.getTableName(), values, where, whereArgs);
        } catch (SQLException | IllegalArgumentException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Delete from this table
     * @param where
     * @param whereArgs
     * @return number of rows
     */
    public int delete(String where, String[] whereArgs) {
        int result = -1;
        try {
            return mDbManager.delete(this.getTableName(), where, whereArgs);
        } catch (SQLException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Query this table
     * @param projection
     * @param where
     * @param whereArgs
     * @param sortOrder
     * @return Cursor
     */
    public Cursor query(String[] projection, String where, String[] whereArgs, String sortOrder) {
        return this.query(projection, where, whereArgs, null, null, sortOrder, null);
    }

    /**
     * Query this table
     * @param projection
     * @param where
     * @param whereArgs
     * @param groupBy
     * @param having
     * @param sortOrder
     * @param limit
     * @return Cursor
     */
    public Cursor query(String[] projection, String where, String[] whereArgs, @Nullable String groupBy,
                        @Nullable String having, String sortOrder, @Nullable String limit) {
        Cursor c = null;
        try {
            c = mDbManager.query(this.getTableName(), projection, where, whereArgs, groupBy, having, sortOrder, limit);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
        return c;
    }

    /*public Cursor rawQuery(String query, String[] whereArgs) {
        Cursor c = null;
        try {
            return mDbManager.rawQuery(query, whereArgs);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
        }
        return c;
    }*/
}
