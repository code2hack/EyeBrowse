package com.code2hack.eyebrowse.phone;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.code2hack.eyebrowse.core.browser.AddressPolicy;
import com.code2hack.eyebrowse.core.browser.StartupPolicy;

/**
 * The Phone browser surface: address/Open, Back/Forward/Reload, compact status and the one
 * session-owned WebView.
 *
 * <p>The Activity is replaceable; the {@link PhoneBrowserSession} is not. Recreation reattaches the
 * same live document instead of creating a second page. Back dismisses the IME first, then walks
 * WebView history, then leaves the Activity.
 */
public final class MainActivity extends ComponentActivity {

    private static final String STATE_DRAFT = "address_draft";
    private static final String STATE_EDITING = "address_editing";

    private PhoneBrowserSession session;
    private AddressBarModel addressBar;

    private EditText addressInput;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton reloadButton;
    private Button openButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private ViewGroup webContainer;

    private boolean updatingField;

    private final PhoneBrowserSession.Listener sessionListener = session -> render();

    private final TextWatcher addressWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (!updatingField) {
                addressBar.onUserEdit(s.toString());
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        session = PhoneBrowserSession.get(getApplicationContext());
        addressBar = new AddressBarModel();

        addressInput = findViewById(R.id.address_input);
        backButton = findViewById(R.id.button_back);
        forwardButton = findViewById(R.id.button_forward);
        reloadButton = findViewById(R.id.button_reload);
        openButton = findViewById(R.id.button_open);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        webContainer = findViewById(R.id.web_container);

        if (savedInstanceState != null) {
            String draft = savedInstanceState.getString(STATE_DRAFT, "");
            addressBar.onUserEdit(draft);
            if (!savedInstanceState.getBoolean(STATE_EDITING, false)) {
                addressBar.onSubmittedAccepted(draft);
            }
            setFieldText(addressBar.draft());
        }

        addressInput.addTextChangedListener(addressWatcher);
        addressInput.setOnEditorActionListener((view, actionId, event) -> {
            submitAddress();
            return true;
        });
        openButton.setOnClickListener(view -> submitAddress());
        backButton.setOnClickListener(view -> session.goBack());
        forwardButton.setOnClickListener(view -> session.goForward());
        reloadButton.setOnClickListener(view -> session.reload());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isImeVisible()) {
                    hideIme();
                    return;
                }
                if (session.canGoBack()) {
                    session.goBack();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        });

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        session.addListener(sessionListener);
        session.attach(this, webContainer);
        if (session.startupDecision() != StartupPolicy.Decision.REATTACH_LIVE_SESSION) {
            addressBar.syncTo(session.lastCommittedUrl());
            setFieldText(addressBar.draft());
        }
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_DRAFT, addressBar.draft());
        outState.putBoolean(STATE_EDITING, addressBar.editing());
    }

    @Override
    protected void onStop() {
        super.onStop();
        session.flushCookies();
    }

    @Override
    protected void onDestroy() {
        session.removeListener(sessionListener);
        session.detach();
        super.onDestroy();
    }

    private void submitAddress() {
        AddressPolicy.Result result = session.openAddress(addressInput.getText().toString());
        if (result.accepted()) {
            addressBar.onSubmittedAccepted(result.url());
            setFieldText(addressBar.draft());
        } else {
            addressBar.onSubmittedRejected();
        }
        hideIme();
        render();
    }

    private void setFieldText(String text) {
        updatingField = true;
        addressInput.setText(text);
        addressInput.setSelection(addressInput.getText().length());
        updatingField = false;
    }

    private void render() {
        String committed = session.displayUrl();
        addressBar.syncTo(committed);
        if (!addressBar.editing() && !addressInput.getText().toString().equals(addressBar.draft())) {
            setFieldText(addressBar.draft());
        }

        String message;
        boolean isError = false;
        if (session.errorMessage() != null) {
            message = session.errorMessage();
            isError = true;
        } else if (session.noticeMessage() != null) {
            message = session.noticeMessage();
        } else if (session.isLoading()) {
            message = getString(R.string.status_loading, committed == null ? "" : committed);
        } else if (committed != null) {
            String title = session.pageTitle();
            message = title == null || title.isEmpty() ? committed : title;
        } else if (session.startupDecision() == StartupPolicy.Decision.OFFER_SAVED_URL) {
            message = getString(R.string.status_recovery);
        } else {
            message = getString(R.string.status_empty);
        }

        statusText.setText(message);
        statusText.setTextColor(getColor(
                isError ? R.color.eyebrowse_error : R.color.eyebrowse_text_secondary));
        statusText.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);

        progressBar.setVisibility(session.isLoading() ? View.VISIBLE : View.INVISIBLE);
        progressBar.setProgress(session.progress());

        backButton.setEnabled(session.canGoBack());
        forwardButton.setEnabled(session.canGoForward());
        reloadButton.setEnabled(session.isLive());
    }

    private boolean isImeVisible() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
        return insets != null && insets.isVisible(WindowInsetsCompat.Type.ime());
    }

    private void hideIme() {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.ime());
        View focused = getCurrentFocus();
        if (focused != null) {
            focused.clearFocus();
        }
    }
}
