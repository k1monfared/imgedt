package com.imgedt.editor;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * A single adjustment row in the Tune panel. Shows the tool name on the left
 * (crossfading to the current value while the slider is being dragged) and
 * a SeekBar on the right. Follows the layout and animation pattern from
 * Telegram's PhotoEditToolCell.
 */
public class TuneToolRow extends FrameLayout {

    public interface Callback {
        void onToolValueChanged(int toolIndex, float value);
    }

    private final TextView nameTextView;
    private final TextView valueTextView;
    private final SeekBar seekBar;
    private final int toolIndex;
    private final boolean centered;

    private AnimatorSet valueAnimation;
    private final Runnable hideValueRunnable = this::hideValue;

    public TuneToolRow(Context context, int toolIndex, String name, boolean centered,
                       float initialValue, Callback callback) {
        super(context);
        this.toolIndex = toolIndex;
        this.centered = centered;

        // Name label (left zone, 80dp, right-aligned)
        nameTextView = new TextView(context);
        nameTextView.setGravity(Gravity.RIGHT);
        nameTextView.setTextColor(0xFFFFFFFF);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        nameTextView.setText(name);
        FrameLayout.LayoutParams nameLp = new FrameLayout.LayoutParams(dp(80), LayoutParams.WRAP_CONTENT);
        nameLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        addView(nameTextView, nameLp);

        // Value label (overlays name, same position, cyan color, hidden initially)
        valueTextView = new TextView(context);
        valueTextView.setTextColor(0xFF4FC3F7);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        valueTextView.setGravity(Gravity.RIGHT);
        valueTextView.setSingleLine(true);
        valueTextView.setAlpha(0.0f);
        FrameLayout.LayoutParams valueLp = new FrameLayout.LayoutParams(dp(80), LayoutParams.WRAP_CONTENT);
        valueLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        addView(valueTextView, valueLp);

        // SeekBar (right zone, 96dp left margin, 24dp right margin)
        seekBar = new SeekBar(context);
        if (centered) {
            seekBar.setMax(200);
            seekBar.setProgress((int) (initialValue + 100));
        } else {
            seekBar.setMax(100);
            seekBar.setProgress((int) initialValue);
        }
        FrameLayout.LayoutParams seekLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, dp(40));
        seekLp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        seekLp.leftMargin = dp(96);
        seekLp.rightMargin = dp(24);
        addView(seekBar, seekLp);

        updateValueText(initialValue);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                float value = centered ? (progress - 100) : progress;
                callback.onToolValueChanged(toolIndex, value);
                updateValueText(value);
                showValue();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    public void resetValue() {
        if (valueAnimation != null) {
            valueAnimation.cancel();
            valueAnimation = null;
        }
        removeCallbacks(hideValueRunnable);
        valueTextView.setTag(null);
        valueTextView.setAlpha(0.0f);
        nameTextView.setAlpha(1.0f);

        if (centered) {
            seekBar.setProgress(100);
        } else {
            seekBar.setProgress(0);
        }
        updateValueText(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
    }

    private void updateValueText(float value) {
        int intVal = Math.round(value);
        if (centered && intVal > 0) {
            valueTextView.setText("+" + intVal);
        } else {
            valueTextView.setText(String.valueOf(intVal));
        }
    }

    private void showValue() {
        if (valueTextView.getTag() == null) {
            if (valueAnimation != null) {
                valueAnimation.cancel();
            }
            valueTextView.setTag(1);
            valueAnimation = new AnimatorSet();
            valueAnimation.playTogether(
                    ObjectAnimator.ofFloat(valueTextView, View.ALPHA, 1.0f),
                    ObjectAnimator.ofFloat(nameTextView, View.ALPHA, 0.0f));
            valueAnimation.setDuration(250);
            valueAnimation.setInterpolator(new DecelerateInterpolator());
            valueAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    postDelayed(hideValueRunnable, 1000);
                }
            });
            valueAnimation.start();
        } else {
            removeCallbacks(hideValueRunnable);
            postDelayed(hideValueRunnable, 1000);
        }
    }

    private void hideValue() {
        valueTextView.setTag(null);
        valueAnimation = new AnimatorSet();
        valueAnimation.playTogether(
                ObjectAnimator.ofFloat(valueTextView, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(nameTextView, View.ALPHA, 1.0f));
        valueAnimation.setDuration(250);
        valueAnimation.setInterpolator(new DecelerateInterpolator());
        valueAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(valueAnimation)) {
                    valueAnimation = null;
                }
            }
        });
        valueAnimation.start();
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
