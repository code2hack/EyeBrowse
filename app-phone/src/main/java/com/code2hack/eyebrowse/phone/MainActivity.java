package com.code2hack.eyebrowse.phone;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.code2hack.eyebrowse.core.browser.AddressPolicy;
import com.code2hack.eyebrowse.core.browser.StartupPolicy;

/**
 * The Phone browser surface: address/Open, Back/Forward/Reload, compact status, explicit hosting
 * Start/Stop and the one session-owned WebView.
 *
 * <p>The Activity is replaceable; the {@link PhoneBrowserSession} is not. Recreation reattaches the
 * same live document instead of creating a second page. While hosting is active, backgrounding the
 * Activity moves the live WebView to the private presentation and returning reattaches it; the
 * hosting service outlives the Activity. Back dismisses the IME first, then walks WebView history,
 * then leaves the Activity.
 */
public final class MainActivity extends ComponentActivity {

    private static final String STATE_DRAFT = "address_draft";
    private static final String STATE_EDITING = "address_editing";

    private PhoneBrowserSession session;
    private PhoneBrowserSession.Attachment attachment;
    private AddressBarModel addressBar;
    private HostingController hosting;

    private EditText addressInput;
    private ImageButton backButton;
    private ImageButton forwardButton;
    private ImageButton reloadButton;
    private Button openButton;
    private Button hostingButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView hostingStatusText;
    private ViewGroup webContainer;

    private boolean updatingField;

    private final PhoneBrowserSession.Listener sessionListener = session -> {
        ensureAttached();
        render();
    };

    private final HostingController.Listener hostingListener = () -> render();

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
        Log.i("EyeBrowseHost", "activity onCreate " + identityHash());
        setContentView(R.layout.activity_main);

        session = PhoneBrowserSession.get(getApplicationContext());
        addressBar = new AddressBarModel();

        addressInput = findViewById(R.id.address_input);
        backButton = findViewById(R.id.button_back);
        forwardButton = findViewById(R.id.button_forward);
        reloadButton = findViewById(R.id.button_reload);
        openButton = findViewById(R.id.button_open);
        hostingButton = findViewById(R.id.button_hosting_toggle);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        hostingStatusText = findViewById(R.id.hosting_status);
        webContainer = findViewById(R.id.web_container);
        hosting = HostingController.get(getApplicationContext());

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
        hostingButton.setOnClickListener(view -> toggleHosting());

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
        hosting.addListener(hostingListener);
        attachment = session.attach(this, webContainer);
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
    protected void onStart() {
        super.onStart();
        Log.i("EyeBrowseHost", "activity onStart " + identityHash());
        HostingController.Status hostingStatus = hosting.status();
        if (hostingStatus.state == HostingController.State.HOSTING
                && hostingStatus.attachment == HostingController.Attachment.PRIVATE_DISPLAY) {
            // A hosting presentation owns the view while backgrounded; returning Phone UI takes it
            // back and receives the fresh ownership token.
            PhoneBrowserSession.Attachment token = hosting.moveWebViewToPhoneUi(this, webContainer);
            if (token != null) {
                attachment = token;
            }
        } else if (session.view() != null && session.view().getParent() != webContainer) {
            // The resuming Activity takes the view from wherever it lives (parentless after a
            // backgrounded Stop, or held by a finishing Activity in transition ordering).
            attachment = session.attach(this, webContainer);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("EyeBrowseHost", "activity onStop " + identityHash() + " finishing=" + isFinishing());
        session.flushCookies();
        // While hosting, a backgrounding Activity hands the live WebView to the private
        // presentation. A finishing Activity keeps its token: its onDestroy path decides, because
        // system transition ordering can run another Activity's onStart before this onStop.
        HostingController.Status hostingStatus = hosting.status();
        if (!isFinishing() && hostingStatus.state == HostingController.State.HOSTING
                && hostingStatus.attachment == HostingController.Attachment.PHONE_UI) {
            hosting.moveWebViewToPrivateDisplay(attachment);
            attachment = null;
        }
    }

    @Override
    protected void onDestroy() {
        Log.i("EyeBrowseHost", "activity onDestroy " + identityHash());
        hosting.removeListener(hostingListener);
        session.removeListener(sessionListener);
        // A destroyed Activity must not steal the view from a successor; only when this Activity
        // still owns the session does the host move the view offscreen and keep hosting alive.
        hosting.moveWebViewToPrivateDisplay(attachment);
        session.detach(attachment);
        super.onDestroy();
    }

    private String identityHash() {
        return "act=" + System.identityHashCode(this);
    }

    private void toggleHosting() {
        HostingController.Status hostingStatus = hosting.status();
        Log.i("EyeBrowseHost", "toggleHosting state=" + hostingStatus.state);
        if (hostingStatus.state == HostingController.State.HOSTING
                || hostingStatus.state == HostingController.State.STARTING
                || hostingStatus.state == HostingController.State.STOPPING) {
            hosting.stop();
        } else {
            requestNotificationPermissionIfNeeded();
            hosting.start();
        }
        render();
    }

    /**
     * One ordinary runtime-permission request when starting hosting. Hosting proceeds regardless of
     * the outcome; if notifications are denied the foreground-service notification is suppressed by
     * the platform and the in-app Stop control remains available.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission
                        .POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    /** Reattaches a live parentless view (e.g. after Stop released the hosting presentation). */
    private void ensureAttached() {
        if (!getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            return; // Never steal the hosted view while this Activity is backgrounded.
        }
        if (session.view() != null && session.view().getParent() == null) {
            attachment = session.attach(this, webContainer);
        }
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
        StartupPolicy.Decision decision = session.startupDecision();
        if (session.errorMessage() != null) {
            message = session.errorMessage();
            isError = true;
        } else if (session.noticeMessage() != null) {
            message = session.noticeMessage();
        } else if (session.isLoading()) {
            message = getString(R.string.status_loading, committed == null ? "" : committed);
        } else if (decision == StartupPolicy.Decision.OFFER_SAVED_URL) {
            // Not live: never present the saved address as an existing page.
            message = getString(R.string.status_recovery);
        } else if (committed != null) {
            String title = session.pageTitle();
            message = title == null || title.isEmpty() ? committed : title;
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

        renderHosting();
    }

    private void renderHosting() {
        HostingController.Status hostingStatus = hosting.status();
        String text;
        switch (hostingStatus.state) {
            case HOSTING:
                text = getString(R.string.hosting_status_active, hostingStatus.generation,
                        getString(hostingStatus.captureActive ? R.string.hosting_capture_active
                                : R.string.hosting_capture_idle));
                hostingButton.setText(R.string.action_hosting_stop);
                break;
            case STARTING:
            case STOPPING:
                text = getString(R.string.hosting_status_starting);
                hostingButton.setText(R.string.action_hosting_stop);
                break;
            case NOT_HOSTING:
            default:
                text = hostingStatus.failureReason == null
                        ? getString(R.string.hosting_status_idle)
                        : getString(R.string.hosting_status_failed, hostingStatus.failureReason);
                hostingButton.setText(R.string.action_hosting_start);
                break;
        }
        hostingStatusText.setText(text);
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
