package com.vssh.dbmanplus;

import android.database.Cursor;
import android.database.CursorWrapper;

/**
 * Created by varun on 25.08.15.
 *
 * Cursor extension used with DbManPlus.
 */
public class DbManCursor extends CursorWrapper {
    private DbManPlus mDbManager;
    /**
     * Creates a cursor wrapper.
     *
     * @param cursor The underlying cursor to wrap.
     */
    public DbManCursor(Cursor cursor, DbManPlus dbManager) {
        super(cursor);
        this.mDbManager = dbManager;
    }

    @Override
    public void close() {
        super.close();
        mDbManager.close();
    }
}
