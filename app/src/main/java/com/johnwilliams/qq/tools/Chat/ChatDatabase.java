package com.johnwilliams.qq.tools.Chat;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

@Database(entities = {Chat.class}, version = 1, exportSchema = false)
public abstract class ChatDatabase extends RoomDatabase {
    public abstract ChatDao chatDao();

    private static volatile ChatDatabase INSTANCE;

    static ChatDatabase getDatabase(final Context context){
        if (INSTANCE == null){
            synchronized (ChatDatabase.class){
                if (INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            ChatDatabase.class, "chats_database")
                            .addCallback(sRoomDatabaseCallback).build();
                }
            }
        }
        return INSTANCE;
    }

    private static Callback sRoomDatabaseCallback =
            new Callback(){

                @Override
                public void onOpen (@NonNull SupportSQLiteDatabase db){
                    super.onOpen(db);
                    new PopulateDbAsync(INSTANCE).execute();
                }
            };

    private static class PopulateDbAsync extends AsyncTask<Void, Void, Void>{

        private final ChatDao mDao;

        PopulateDbAsync(ChatDatabase db){
            mDao = db.chatDao();
        }

        @Override
        protected Void doInBackground(final Void... params){
//            mDao.deleteAll();
            Chat chat = new Chat("2016011451", "佳", new Date().getTime(), 1, "亮亮我爱你");
            mDao.insert(chat);
            chat = new Chat("2016011452", "赵文亮",
                    new Date().getTime() - 10000000000L, 1, "佳佳我爱你");
            mDao.insert(chat);
//            Toast.makeText(null, "Love Lucy", Toast.LENGTH_LONG).show();
            return null;
        }
    }
}
