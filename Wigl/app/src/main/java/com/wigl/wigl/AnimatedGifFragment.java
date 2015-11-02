package com.wigl.wigl;

import android.app.Fragment;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

public class AnimatedGifFragment extends Fragment {
    private static final String TAG = "AnimatedGifFragment";

    private View mView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.animated_gif, container);
        return mView;
    }

    public void showWigl(String... paths) {
        ImageView animationView = (ImageView) mView.findViewById(R.id.ivAnimation);
        AnimationDrawable animation = new AnimationDrawable();
        animation.setOneShot(false);
        for (String path : paths) {
            Log.d(TAG, "Creating drawable from " + path);
            Drawable drawable = Drawable.createFromPath(path);
            Log.d(TAG, "Got drawable " + drawable);
            if (drawable != null)
                animation.addFrame(drawable, 10);
        }
        animationView.setImageDrawable(animation);
        animation.start();
    }
}
