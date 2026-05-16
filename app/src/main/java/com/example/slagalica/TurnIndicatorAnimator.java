package com.example.slagalica;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

final class TurnIndicatorAnimator {
    private final TextView leftAvatar;
    private final TextView rightAvatar;
    private AnimatorSet leftAnimator;
    private AnimatorSet rightAnimator;

    TurnIndicatorAnimator(TextView leftAvatar, TextView rightAvatar) {
        this.leftAvatar = leftAvatar;
        this.rightAvatar = rightAvatar;
    }

    void setActivePlayer(Integer activePlayer) {
        boolean leftActive = activePlayer != null && activePlayer == 1;
        boolean rightActive = activePlayer != null && activePlayer == 2;
        if (leftActive) {
            startLeft();
        } else {
            stopLeft();
        }
        if (rightActive) {
            startRight();
        } else {
            stopRight();
        }
    }

    void clear() {
        stopLeft();
        stopRight();
    }

    private void startLeft() {
        if (leftAnimator == null) {
            leftAnimator = buildAnimator(leftAvatar);
        }
        if (!leftAnimator.isStarted()) {
            leftAnimator.start();
        }
    }

    private void startRight() {
        if (rightAnimator == null) {
            rightAnimator = buildAnimator(rightAvatar);
        }
        if (!rightAnimator.isStarted()) {
            rightAnimator.start();
        }
    }

    private void stopLeft() {
        if (leftAnimator != null) {
            leftAnimator.cancel();
        }
        reset(leftAvatar);
    }

    private void stopRight() {
        if (rightAnimator != null) {
            rightAnimator.cancel();
        }
        reset(rightAvatar);
    }

    private AnimatorSet buildAnimator(View target) {
        ObjectAnimator pulseX = ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 1.03f, 1f);
        pulseX.setDuration(900);
        pulseX.setRepeatCount(ObjectAnimator.INFINITE);
        pulseX.setInterpolator(new LinearInterpolator());

        ObjectAnimator pulseY = ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 1.03f, 1f);
        pulseY.setDuration(900);
        pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseY.setInterpolator(new LinearInterpolator());

        ObjectAnimator shake = ObjectAnimator.ofFloat(target, View.TRANSLATION_X, 0f, 0.7f, -0.7f, 0.35f, -0.35f, 0f);
        shake.setDuration(380);
        shake.setRepeatCount(ObjectAnimator.INFINITE);
        shake.setInterpolator(new LinearInterpolator());

        AnimatorSet set = new AnimatorSet();
        set.playTogether(pulseX, pulseY, shake);
        return set;
    }

    private void reset(View target) {
        target.setTranslationX(0f);
        target.setScaleX(1f);
        target.setScaleY(1f);
    }
}
