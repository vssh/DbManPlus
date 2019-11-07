package com.vssh.dbmanplus;

import android.content.ContentValues;
import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Created by varun on 06.11.16.
 *
 * Helper class for DbManPlus. Models the items in the tables.
 */

public abstract class DbItem {
    public static final Unchanged UNCHANGED = new Unchanged();

    protected @NonNull ContentValues toContentValues(String[] columns) {
        ContentValues values = new ContentValues();
        for(String column : columns) {
            Object val = getVal(column);

            if(UNCHANGED.equals(val))
                continue;

            if(val == null)
                values.putNull(column);
            else if(val instanceof Integer)
                values.put(column, (Integer) val);
            else if(val instanceof Long)
                values.put(column, (Long) val);
            else if(val instanceof Float)
                values.put(column, (Float) val);
            else if(val instanceof Double)
                values.put(column, (Double) val);
            else if(val instanceof String)
                values.put(column, (String) val);
            else if(val instanceof Byte)
                values.put(column, (Byte) val);
            else if(val instanceof Boolean)
                values.put(column, (Boolean) val);
            else if(val instanceof Short)
                values.put(column, (Short) val);
            else if(val instanceof byte[])
                values.put(column, (byte[]) val);
        }
        return values;
    }

    protected void fromCursor(Cursor cursor, String[] columns) {
        for(String column : columns) {
            int index = cursor.getColumnIndex(column);
            if(index != -1) {
                int type = cursor.getType(index);
                switch (type) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        setVal(column, cursor.getLong(index), null, null, null);
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        setVal(column, null, cursor.getDouble(index), null, null);
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        setVal(column, null, null, cursor.getString(index), null);
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        setVal(column, null, null, null, cursor.getBlob(index));
                        break;
                    default:
                        setVal(column, null, null, null, null);
                }
            }
        }
    }

    private static class Unchanged {}

    protected abstract void setVal(String column, Long integerVal, Double floatingPointVal, String textVal, byte[] blobVal);
    protected abstract @Nullable Object getVal(String column);
}
