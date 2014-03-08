package com.locationstream;

import android.content.SharedPreferences.Editor;

public interface WebServiceApi {
    abstract public boolean loginUser(String login, String password, Editor editor);
    abstract public boolean logoutUser(String login, String passwd, Editor editor);
    abstract public boolean hasValidSession();
}
