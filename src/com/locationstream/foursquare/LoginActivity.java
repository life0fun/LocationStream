package com.locationstream.foursquare;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.locationstream.AppPreferences;
import com.locationstream.LocationSensorApp;
import com.locationstream.R;
import com.locationstream.LocationSensorApp.LSAppLog;


public class LoginActivity extends Activity {
    public static final String TAG = "API_FSLogin";

    private AsyncTask<Void, Void, Boolean> mLoginTask;

    private FoursquareHttpApi mFSApi;
    
    private TextView mNewAccountTextView;
    private EditText mPhoneUsernameEditText;
    private EditText mPasswordEditText;

    private ProgressDialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.login_activity);

        mFSApi =  ((LocationSensorApp) getApplication()).getCheckin().getFoursquare();
        
        // if login screen is created, clear old credentials
        mFSApi.logoutUser(null, null, AppPreferences.mPref.edit());
                
        // Set up the UI.
        ensureUi();

        // Re-task if the request was cancelled.
        mLoginTask = (LoginTask) getLastNonConfigurationInstance();
        if (mLoginTask != null && mLoginTask.isCancelled()) {
            mLoginTask = new LoginTask().execute();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mLoginTask != null) {
            mLoginTask.cancel(true);
        }
        return mLoginTask;
    }

    private ProgressDialog showProgressDialog() {
        if (mProgressDialog == null) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setTitle(R.string.login_dialog_title);
            dialog.setMessage(getString(R.string.login_dialog_message));
            dialog.setIndeterminate(true);
            dialog.setCancelable(true);
            mProgressDialog = dialog;
        }
        mProgressDialog.show();
        return mProgressDialog;
    }

    private void dismissProgressDialog() {
        try {
            mProgressDialog.dismiss();
        } catch (IllegalArgumentException e) {
            // We don't mind. android cleared it for us.
        }
    }

    private void ensureUi() {
        final Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mLoginTask = new LoginTask().execute();
            }
        });

        mNewAccountTextView = (TextView) findViewById(R.id.newAccountTextView);
        mNewAccountTextView.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(
                    Intent.ACTION_VIEW, Uri.parse("http://m.foursquare.com/signup")));
            }
        });

        mPhoneUsernameEditText = ((EditText) findViewById(R.id.phoneEditText));
        mPasswordEditText = ((EditText) findViewById(R.id.passwordEditText));

        TextWatcher fieldValidatorTextWatcher = new TextWatcher() {
            public void afterTextChanged(Editable s) {
            }
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                button.setEnabled(phoneNumberEditTextFieldIsValid()
                        && passwordEditTextFieldIsValid());
            }

            private boolean phoneNumberEditTextFieldIsValid() {
                // This can be either a phone number or username so we don't
                // care too much about the
                // format.
                return !TextUtils.isEmpty(mPhoneUsernameEditText.getText());
            }

            private boolean passwordEditTextFieldIsValid() {
                return !TextUtils.isEmpty(mPasswordEditText.getText());
            }
        };

        mPhoneUsernameEditText.addTextChangedListener(fieldValidatorTextWatcher);
        mPasswordEditText.addTextChangedListener(fieldValidatorTextWatcher);
    }

    // login just remeber user's credential..not validated through web site.
    // in case credential wrong, got http 401 exception and handle from there!!!
    private class LoginTask extends AsyncTask<Void, Void, Boolean> 
    {
        private Exception mReason;

        protected void onPreExecute() {
            showProgressDialog();
        }

        @Override
        protected Boolean doInBackground(Void... params) {  
        	// no validation...http 401 to indicate credential wrong!!
            try {
                String phoneNumber = mPhoneUsernameEditText.getText().toString();
                String password = mPasswordEditText.getText().toString();
                boolean loggedIn = mFSApi.loginUser(phoneNumber, password, AppPreferences.mPref.edit());
                LSAppLog.d(TAG, "LoginTask:" + phoneNumber + ":" + password + ":ret:" + loggedIn);
                return loggedIn;
            } catch (Exception e) {
                mReason = e;
                mFSApi.logoutUser(null, null, AppPreferences.mPref.edit());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean loggedIn) {
            LocationSensorApp lsapp = (LocationSensorApp) getApplication();

            if (loggedIn) {
                //sendBroadcast(new Intent(lsapp.INTENT_ACTION_LOGGED_IN));
                Toast.makeText(LoginActivity.this, getString(R.string.login_welcome_toast),
                        Toast.LENGTH_LONG).show();

                // Launch the service to update any widgets, etc.
                // lsapp.requestStartService();

                // Launch the main activity to let the user do anything.
                Intent intent = new Intent(LoginActivity.this, FoursquareMainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);  // start the parent main UI that created this UI.

                // Be done with the activity.
                finish();
            } else {
                //sendBroadcast(new Intent(LocationSensorApp.INTENT_ACTION_LOGGED_OUT));
            }
            dismissProgressDialog();
        }

        @Override
        protected void onCancelled() {
            dismissProgressDialog();
        }
    }
}
