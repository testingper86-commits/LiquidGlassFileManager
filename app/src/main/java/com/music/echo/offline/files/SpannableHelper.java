package com.music.echo.offline.files;

import com.music.echo.offline.R;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

final class SpannableHelper {
    /** Colours the leading "●" of a legend label. */
    static void dot(TextView tv, int color) {
        SpannableString s = new SpannableString(tv.getText());
        s.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(s);
    }
}
