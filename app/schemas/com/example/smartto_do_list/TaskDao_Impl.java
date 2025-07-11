package com.example.smartto_do_list;

import androidx.annotation.NonNull;
import androidx.room.EntityDeleteOrUpdateAdapter;
import androidx.room.EntityInsertAdapter;
import androidx.room.RoomDatabase;
import androidx.room.util.DBUtil;
import androidx.room.util.SQLiteStatementUtil;
import androidx.sqlite.SQLiteStatement;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation", "removal"})
public final class TaskDao_Impl implements TaskDao {
  private final RoomDatabase __db;

  private final EntityInsertAdapter<Task> __insertAdapterOfTask;

  private final EntityDeleteOrUpdateAdapter<Task> __deleteAdapterOfTask;

  private final EntityDeleteOrUpdateAdapter<Task> __updateAdapterOfTask;

  public TaskDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertAdapterOfTask = new EntityInsertAdapter<Task>() {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `tasks` (`id`,`title`,`date`,`time`,`location`,`priority`,`category`,`reminder`,`notes`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SQLiteStatement statement, final Task entity) {
        statement.bindLong(1, entity.id);
        if (entity.title == null) {
          statement.bindNull(2);
        } else {
          statement.bindText(2, entity.title);
        }
        if (entity.date == null) {
          statement.bindNull(3);
        } else {
          statement.bindText(3, entity.date);
        }
        if (entity.time == null) {
          statement.bindNull(4);
        } else {
          statement.bindText(4, entity.time);
        }
        if (entity.location == null) {
          statement.bindNull(5);
        } else {
          statement.bindText(5, entity.location);
        }
        if (entity.priority == null) {
          statement.bindNull(6);
        } else {
          statement.bindText(6, entity.priority);
        }
        if (entity.category == null) {
          statement.bindNull(7);
        } else {
          statement.bindText(7, entity.category);
        }
        if (entity.reminder == null) {
          statement.bindNull(8);
        } else {
          statement.bindText(8, entity.reminder);
        }
        if (entity.notes == null) {
          statement.bindNull(9);
        } else {
          statement.bindText(9, entity.notes);
        }
      }
    };
    this.__deleteAdapterOfTask = new EntityDeleteOrUpdateAdapter<Task>() {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `tasks` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SQLiteStatement statement, final Task entity) {
        statement.bindLong(1, entity.id);
      }
    };
    this.__updateAdapterOfTask = new EntityDeleteOrUpdateAdapter<Task>() {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `tasks` SET `id` = ?,`title` = ?,`date` = ?,`time` = ?,`location` = ?,`priority` = ?,`category` = ?,`reminder` = ?,`notes` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SQLiteStatement statement, final Task entity) {
        statement.bindLong(1, entity.id);
        if (entity.title == null) {
          statement.bindNull(2);
        } else {
          statement.bindText(2, entity.title);
        }
        if (entity.date == null) {
          statement.bindNull(3);
        } else {
          statement.bindText(3, entity.date);
        }
        if (entity.time == null) {
          statement.bindNull(4);
        } else {
          statement.bindText(4, entity.time);
        }
        if (entity.location == null) {
          statement.bindNull(5);
        } else {
          statement.bindText(5, entity.location);
        }
        if (entity.priority == null) {
          statement.bindNull(6);
        } else {
          statement.bindText(6, entity.priority);
        }
        if (entity.category == null) {
          statement.bindNull(7);
        } else {
          statement.bindText(7, entity.category);
        }
        if (entity.reminder == null) {
          statement.bindNull(8);
        } else {
          statement.bindText(8, entity.reminder);
        }
        if (entity.notes == null) {
          statement.bindNull(9);
        } else {
          statement.bindText(9, entity.notes);
        }
        statement.bindLong(10, entity.id);
      }
    };
  }

  @Override
  public void insert(final Task task) {
    DBUtil.performBlocking(__db, false, true, (_connection) -> {
      __insertAdapterOfTask.insert(_connection, task);
      return null;
    });
  }

  @Override
  public void delete(final Task task) {
    DBUtil.performBlocking(__db, false, true, (_connection) -> {
      __deleteAdapterOfTask.handle(_connection, task);
      return null;
    });
  }

  @Override
  public void update(final Task task) {
    DBUtil.performBlocking(__db, false, true, (_connection) -> {
      __updateAdapterOfTask.handle(_connection, task);
      return null;
    });
  }

  @Override
  public List<Task> getAllTasks() {
    final String _sql = "SELECT * FROM tasks";
    return DBUtil.performBlocking(__db, true, false, (_connection) -> {
      final SQLiteStatement _stmt = _connection.prepare(_sql);
      try {
        final int _columnIndexOfId = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "id");
        final int _columnIndexOfTitle = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "title");
        final int _columnIndexOfDate = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "date");
        final int _columnIndexOfTime = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "time");
        final int _columnIndexOfLocation = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "location");
        final int _columnIndexOfPriority = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "priority");
        final int _columnIndexOfCategory = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "category");
        final int _columnIndexOfReminder = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "reminder");
        final int _columnIndexOfNotes = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "notes");
        final List<Task> _result = new ArrayList<Task>();
        while (_stmt.step()) {
          final Task _item;
          _item = new Task();
          _item.id = (int) (_stmt.getLong(_columnIndexOfId));
          if (_stmt.isNull(_columnIndexOfTitle)) {
            _item.title = null;
          } else {
            _item.title = _stmt.getText(_columnIndexOfTitle);
          }
          if (_stmt.isNull(_columnIndexOfDate)) {
            _item.date = null;
          } else {
            _item.date = _stmt.getText(_columnIndexOfDate);
          }
          if (_stmt.isNull(_columnIndexOfTime)) {
            _item.time = null;
          } else {
            _item.time = _stmt.getText(_columnIndexOfTime);
          }
          if (_stmt.isNull(_columnIndexOfLocation)) {
            _item.location = null;
          } else {
            _item.location = _stmt.getText(_columnIndexOfLocation);
          }
          if (_stmt.isNull(_columnIndexOfPriority)) {
            _item.priority = null;
          } else {
            _item.priority = _stmt.getText(_columnIndexOfPriority);
          }
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _item.category = null;
          } else {
            _item.category = _stmt.getText(_columnIndexOfCategory);
          }
          if (_stmt.isNull(_columnIndexOfReminder)) {
            _item.reminder = null;
          } else {
            _item.reminder = _stmt.getText(_columnIndexOfReminder);
          }
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _item.notes = null;
          } else {
            _item.notes = _stmt.getText(_columnIndexOfNotes);
          }
          _result.add(_item);
        }
        return _result;
      } finally {
        _stmt.close();
      }
    });
  }

  @Override
  public Task getTaskById(final int taskId) {
    final String _sql = "SELECT * FROM tasks WHERE id = ? LIMIT 1";
    return DBUtil.performBlocking(__db, true, false, (_connection) -> {
      final SQLiteStatement _stmt = _connection.prepare(_sql);
      try {
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, taskId);
        final int _columnIndexOfId = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "id");
        final int _columnIndexOfTitle = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "title");
        final int _columnIndexOfDate = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "date");
        final int _columnIndexOfTime = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "time");
        final int _columnIndexOfLocation = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "location");
        final int _columnIndexOfPriority = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "priority");
        final int _columnIndexOfCategory = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "category");
        final int _columnIndexOfReminder = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "reminder");
        final int _columnIndexOfNotes = SQLiteStatementUtil.getColumnIndexOrThrow(_stmt, "notes");
        final Task _result;
        if (_stmt.step()) {
          _result = new Task();
          _result.id = (int) (_stmt.getLong(_columnIndexOfId));
          if (_stmt.isNull(_columnIndexOfTitle)) {
            _result.title = null;
          } else {
            _result.title = _stmt.getText(_columnIndexOfTitle);
          }
          if (_stmt.isNull(_columnIndexOfDate)) {
            _result.date = null;
          } else {
            _result.date = _stmt.getText(_columnIndexOfDate);
          }
          if (_stmt.isNull(_columnIndexOfTime)) {
            _result.time = null;
          } else {
            _result.time = _stmt.getText(_columnIndexOfTime);
          }
          if (_stmt.isNull(_columnIndexOfLocation)) {
            _result.location = null;
          } else {
            _result.location = _stmt.getText(_columnIndexOfLocation);
          }
          if (_stmt.isNull(_columnIndexOfPriority)) {
            _result.priority = null;
          } else {
            _result.priority = _stmt.getText(_columnIndexOfPriority);
          }
          if (_stmt.isNull(_columnIndexOfCategory)) {
            _result.category = null;
          } else {
            _result.category = _stmt.getText(_columnIndexOfCategory);
          }
          if (_stmt.isNull(_columnIndexOfReminder)) {
            _result.reminder = null;
          } else {
            _result.reminder = _stmt.getText(_columnIndexOfReminder);
          }
          if (_stmt.isNull(_columnIndexOfNotes)) {
            _result.notes = null;
          } else {
            _result.notes = _stmt.getText(_columnIndexOfNotes);
          }
        } else {
          _result = null;
        }
        return _result;
      } finally {
        _stmt.close();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
