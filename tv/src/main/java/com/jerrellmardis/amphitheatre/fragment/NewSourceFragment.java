package com.jerrellmardis.amphitheatre.fragment;

import com.jerrellmardis.amphitheatre.R;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import butterknife.ButterKnife;
import butterknife.InjectView;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by rharter on 9/11/14.
 */
public class NewSourceFragment extends DialogFragment {
    private static final String TAG = NewSourceFragment.class.getSimpleName();

    private static final String KEY_PATH = "path";

    private static final String DEFAULT_PATH = "smb://";

    private Stack<PathComponent> mPathStack = new Stack<PathComponent>();

    @InjectView(R.id.select) Button mSelect;
    @InjectView(R.id.list_container) FrameLayout mListContainer;
    @InjectView(R.id.error) TextView mErrorText;
    @InjectView(R.id.loading) ProgressBar mLoading;

    private int mRevealCenterX, mRevealCenterY;
    private boolean mInitialLoad = true;

    @Nullable @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_add_source, container, false);
        ButterKnife.inject(this, v);
        return v;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        String path = DEFAULT_PATH;
        if (savedInstanceState != null) {
            path = savedInstanceState.getString(KEY_PATH, DEFAULT_PATH);
        }

        showLoading();
        loadPath(path);
    }

    private void showLoading() {
        mListContainer.setVisibility(View.GONE);
        mErrorText.setVisibility(View.GONE);
        mLoading.setVisibility(View.VISIBLE);
    }

    private void showError(String text) {
        mListContainer.setVisibility(View.GONE);
        mLoading.setVisibility(View.GONE);
        mErrorText.setVisibility(View.VISIBLE);
        mErrorText.setText(text);
    }

    private void showList() {
        mErrorText.setVisibility(View.GONE);
        mLoading.setVisibility(View.GONE);
        mListContainer.setVisibility(View.VISIBLE);
    }

    private void loadPath(String path) {
        loadPath(new PathComponent(path));
    }

    private void loadPath(PathComponent path) {
        if (mPathStack.empty() || !mPathStack.peek().equals(path)) {
            mPathStack.push(path);
        }
        new LoadPathTask().execute(path);
    }

    private void onLoadPathResult(PathComponent component) {
        switch (component.status) {
            case PathComponent.STATUS_SUCCESS:
                showList();
                boolean animate = !mInitialLoad;
                mInitialLoad = false;
                showFolders(component.files, animate);
                break;
            case PathComponent.STATUS_ERROR:
                showError(component.message);
                break;
            case PathComponent.STATUS_NEEDS_AUTH:
                promptForAuth(component);
        }
    }

    private void promptForAuth(final PathComponent component) {
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_auth, null);
        final EditText user = (EditText) v.findViewById(R.id.username);
        final EditText pass = (EditText) v.findViewById(R.id.password);

        new AlertDialog.Builder(getActivity())
                .setView(v)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        component.username = user.getText().toString();
                        component.password = pass.getText().toString();
                        loadPath(component);
                        dialog.dismiss();
                    }
                }).show();
    }

    private void showFolders(List<String> files, boolean animated) {
        final ListView l = createFolderList(files);

        if (animated) {
            l.setVisibility(View.GONE);
            mListContainer.addView(l);
            ValueAnimator anim = ViewAnimationUtils.createCircularReveal(l, mRevealCenterX,
                    mRevealCenterY, 0, mListContainer.getHeight());
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    l.setVisibility(View.VISIBLE);
                    mListContainer.removeViewAt(0);
                }
            });
            anim.start();
        } else {
            if (mListContainer.getChildCount() > 0) {
                mListContainer.removeViewAt(0);
            }
            mListContainer.addView(l);
        }
    }

    private ListView createFolderList(List<String> folders) {
        List<String> f = new ArrayList<String>(folders);
        if (mPathStack.size() > 0) {
            f.add(0, "..");
        }
        ListView l = new ListView(getActivity());
        l.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> adapterView, View view, int i,
                    long l) {
                onListItemClick(adapterView, view, i, l);
            }
        });
        l.setAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, f));
        return l;
    }

    private void onListItemClick(AdapterView<?> adapterView, View view, int position, long id) {
        mRevealCenterX = view.getLeft() + view.getWidth() / 2;
        mRevealCenterY = view.getTop() + view.getHeight() / 2;

        String path = (String) adapterView.getItemAtPosition(position);
        if ("..".equals(path)) {
            mPathStack.pop();
            loadPath(mPathStack.peek());
        } else {
            loadPath(path);
        }
    }

    private class LoadPathTask extends AsyncTask<PathComponent, Void, PathComponent> {

        @Override protected PathComponent doInBackground(PathComponent... paths) {
            PathComponent component = paths[0];

            NtlmPasswordAuthentication auth = null;
            if (!TextUtils.isEmpty(component.username)) {
                auth = new NtlmPasswordAuthentication("", component.username, component.password);
            }
            SmbFile[] files = new SmbFile[0];
            try {
                files = new SmbFile(component.path, auth).listFiles();
                component.status = PathComponent.STATUS_SUCCESS;
            } catch (SmbAuthException e) {
                // Auth exceptions are expected, simply set the status and return
                component.status = PathComponent.STATUS_NEEDS_AUTH;
                return component;
            } catch (MalformedURLException e) {
                Log.e(TAG, "Malformed Url: " + component.path, e);
                component.status = PathComponent.STATUS_ERROR;
                component.message = e.getMessage();
            } catch (SmbException e) {
                Log.e(TAG, "SmbException for path " + component.path, e);
                component.status = PathComponent.STATUS_ERROR;
                component.message = e.getMessage();
            }

            component.files = new ArrayList<String>(files.length);
            for (int i = 0; i < files.length; i++) {
                SmbFile file = files[i];
                // Skip hidden shares
                if (file.getPath().endsWith("$/")) {
                    continue;
                }
                component.files.add(file.getPath());
            }

            return component;
        }

        @Override protected void onPostExecute(PathComponent component) {
            onLoadPathResult(component);
        }
    }

    private static class PathComponent {

        static final int STATUS_SUCCESS = 0;
        static final int STATUS_ERROR = 1;
        static final int STATUS_NEEDS_AUTH = 2;

        int status;
        String message;
        String path;
        String username;
        String password;
        List<String> files;

        PathComponent(String path) {
            this.path = path;
        }

        @Override public String toString() {
            return path.substring(path.lastIndexOf('/'));
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof PathComponent)) {
                return false;
            }
            return path.equals(((PathComponent) o).path);
        }
    }
}
