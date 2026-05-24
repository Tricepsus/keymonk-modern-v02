package com.keymonkmodern.dualswipe;

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.inputmethod.InputConnection;

public class DualSwipeImeService extends InputMethodService implements DualSwipeKeyboardView.KeyboardListener {
    @Override
    public View onCreateInputView() {
        DualSwipeKeyboardView view = new DualSwipeKeyboardView(this);
        view.setKeyboardListener(this);
        return view;
    }

    @Override
    public void onText(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text != null && !text.isEmpty()) ic.commitText(text, 1);
    }

    @Override
    public void onBackspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.deleteSurroundingText(1, 0);
    }
}
