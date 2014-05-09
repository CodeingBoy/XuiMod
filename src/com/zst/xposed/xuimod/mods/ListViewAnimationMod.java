/*
 * Copyright (C) 2013 XuiMod
 * This code is based on portions of code by jkl5616, Copyright (C) 2013.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zst.xposed.xuimod.mods;

import android.content.Context;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import com.zst.xposed.xuimod.Common;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class ListViewAnimationMod {
	public static final int ANIMATION_NONE = 0;
	public static final int ANIMATION_WAVE_LEFT = 1;
	public static final int ANIMATION_WAVE_RIGHT = 2;
	public static final int ANIMATION_SCALE = 3;
	public static final int ANIMATION_ALPHA = 4;
	public static final int ANIMATION_STACK_TOP = 5;
	public static final int ANIMATION_STACK_BOTTOM = 6;
	public static final int ANIMATION_UNFOLD = 7;
	public static final int ANIMATION_FOLD = 8;
	public static final int ANIMATION_TRANSLATE_LEFT = 9;
	public static final int ANIMATION_TRANSLATE_RIGHT = 10;
	public static final int ANIMATION_ROTATE = 11;
	public static final int ANIMATION_GOOGLE_NOW_LEFT = 12;
	public static final int ANIMATION_GOOGLE_NOW_RIGHT = 13;
	
	public static final int INTERPOLATOR_NONE = 0;
	public static final int INTERPOLATOR_ACCELERATE = 1;
	public static final int INTERPOLATOR_DECELERATE = 2;
	public static final int INTERPOLATOR_ACCELERATE_DECELERATE = 3;
	public static final int INTERPOLATOR_ANTICIPATE = 4;
	public static final int INTERPOLATOR_OVERSHOOT = 5;
	public static final int INTERPOLATOR_ANTICIPATE_OVERSHOOT = 6;
	public static final int INTERPOLATOR_BOUNCE = 7;
	public static final int INTERPOLATOR_CYCLE = 8;
	public static final int INTERPOLATOR_LINEAR = 9;
	
	static boolean mIsScrolling;
	static int mWidth, mHeight = 0;
	static int mvPosition;
	static int mvViewPosition;
	private static XSharedPreferences mPref;
	private static int mInterpolator;
	private static int cache;
	private static int mAnim;
	private static int mDuration;
	private static boolean mDown = false;

	public static void handleLoadPackage(XSharedPreferences pref) {
		mPref = pref;
		// Get our pref value in String and parse to Integer
		initAbsListView();
		on_Layout();
		reportScrollStateChange();
		obtainView();
	}
	private static void initAbsListView() { 
		XposedBridge.hookAllMethods(AbsListView.class, "initAbsListView", new XC_MethodHook(){
			@Override 
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				AbsListView item = (AbsListView)param.thisObject;
				if (isBlacklisted(item.getContext().getApplicationInfo().packageName)){
					mAnim = ANIMATION_NONE;
					return;
				}
				mHeight = 0; mWidth = 0;
				// Init-ing new AbsListView so we must reset static values from previous view
				mPref.reload();
				mInterpolator = Integer.parseInt(mPref.getString(Common.KEY_LISTVIEW_INTERPOLATOR, Common.DEFAULT_LISTVIEW_INTERPOLATOR) );
				mDuration = mPref.getInt(Common.KEY_LISTVIEW_DURATION, Common.DEFAULT_LISTVIEW_DURATION);
				mAnim = Integer.parseInt(mPref.getString(Common.KEY_LISTVIEW_ANIMATION, Common.DEFAULT_LISTVIEW_ANIMATION) );
				cache = Integer.parseInt(mPref.getString(Common.KEY_LISTVIEW_CACHE, Common.DEFAULT_LISTVIEW_CACHE));
				item.setPersistentDrawingCache(cache);
			}			
		});	
	}
	
	private static boolean isBlacklisted(String currentPkg){
		XSharedPreferences listviewPref = new XSharedPreferences(Common.MY_PACKAGE_NAME, Common.LISTVIEW_PREFERENCE_FILENAME);
		//Get the pref from our custom preference 
		int size = listviewPref.getInt("items" + "_size", 0);
		if(size != 0) {
			for(int i = 0; i < size; i++){
				String pkg = listviewPref.getString("items" + "_" + i, "");
				if(pkg.equals(currentPkg))
					return true;
			}
		}
		return false;
	}
	
	private static void on_Layout() { 
		XposedBridge.hookAllMethods(AbsListView.class, "onLayout", new XC_MethodHook(){
			@Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				AbsListView item = (AbsListView)param.thisObject;
				mHeight = item.getHeight();
		        mWidth = item.getWidth(); 
		        // Called when listView changes layout(rotation, first init)
		        // We then gather size for the below anim codes.
			} 
		});	
	}
	
	private static void reportScrollStateChange() { //TODO documentation
		XposedBridge.hookAllMethods(AbsListView.class, "reportScrollStateChange", new XC_MethodHook(){
			@Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				final int newState = (Integer) param.args[0];
				mIsScrolling = newState != OnScrollListener.SCROLL_STATE_IDLE;
				
				if (!mIsScrolling) return;
				
				final AbsListView lv = (AbsListView) param.thisObject;
				final int currentPosition = lv.getFirstVisiblePosition();
				
				if (currentPosition > mvPosition) {
					mDown = true;
				} else if (currentPosition < mvPosition) {
					mDown = false;
				} else if (currentPosition == mvPosition) {
					// if the view position is the same, we need to be more precise and use pixels
					final int current_view_position = lv.getChildAt(0).getBottom() + lv.getChildAt(0).getTop();
					// add top and bottom together to get more precise comparison
					mDown = mvViewPosition < current_view_position;
					mvViewPosition = current_view_position;
				}
				mvPosition = currentPosition;
				/* Get Direction of Scroll to allow Fold/Unfold animations to animate properly.*/
			}			
		});	
	}
	
	private static void obtainView(){ 
		XposedBridge.hookAllMethods(AbsListView.class, "obtainView", new XC_MethodHook(){
			@Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				if(mIsScrolling) {
					AbsListView thix = (AbsListView)param.thisObject;
					View newResult = setAnimation(thix, (View)param.getResult(),thix.getContext());
					param.setResult(newResult);
				}
				/* In actual ROM, the animation is done before returning view but
				 * In Xposed, we must modify the return value since we can't execute
				 * codes before return. 
				 * 
				 * We use "param.getResult()" to retrieve our view and
				 * apply animation using "setAnimation".
				 * 
				 * After which we return our
				 * animated view using "param.setResult(newResult)"
				 */
			}
		});	
	}
	
	/* Not much documentation for setAnimation as actual commit didnt have any
	 * For more info on animations, you'll have to ask MoKee ROM people who did this
	 */
	private static View setAnimation(Object thisObject, View view, Context mContext) {
		if(mAnim == ANIMATION_NONE) return view;
		if (view == null) return view;
		// Don't create the animation if view is null
		
		Animation anim = null;
		switch (mAnim) {
		case ANIMATION_WAVE_LEFT:
			anim = new ScaleAnimation(0.5f, 1.0f, 0.5f, 1.0f);
			break;
		case ANIMATION_WAVE_RIGHT:
			anim = new ScaleAnimation(0.5f, 1.0f, 0.5f, 1.0f, Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 1.0f);
			break;
		case ANIMATION_SCALE:
			anim = new ScaleAnimation(0.5f, 1.0f, 0.5f, 1.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
			break;
		case ANIMATION_ALPHA:
			anim = new AlphaAnimation(0.0f, 1.0f);
			break;
		case ANIMATION_STACK_TOP:
			anim = new TranslateAnimation(0.0f, 0.0f, -mHeight, 0.0f);
			break;
		case ANIMATION_STACK_BOTTOM:
			anim = new TranslateAnimation(0.0f, 0.0f, mHeight, 0.0f);
			break;
		case ANIMATION_UNFOLD:
			if (mDown)
				anim = new TranslateAnimation(0.0f, 0.0f, -mHeight, 0.0f);
			else
				anim = new TranslateAnimation(0.0f, 0.0f, mHeight, 0.0f);
			break;
		case ANIMATION_FOLD:
			if (mDown)
				anim = new TranslateAnimation(0.0f, 0.0f, mHeight, 0.0f);
			else
				anim = new TranslateAnimation(0.0f, 0.0f, -mHeight, 0.0f);
			break;
		case ANIMATION_TRANSLATE_LEFT:
			anim = new TranslateAnimation(-mWidth, 0.0f, 0.0f, 0.0f);
			break;
		case ANIMATION_TRANSLATE_RIGHT:
			anim = new TranslateAnimation(mWidth, 0.0f, 0.0f, 0.0f);
			break;
		case ANIMATION_ROTATE:
			anim = new RotateAnimation(180, 0.0f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
			break;
		case ANIMATION_GOOGLE_NOW_LEFT:
			AnimationSet animation_set = new AnimationSet(true);
			TranslateAnimation a = new TranslateAnimation(
					Animation.RELATIVE_TO_SELF, 0.0f,
					Animation.RELATIVE_TO_SELF, 0.0f,
					Animation.RELATIVE_TO_SELF, (mDown ? 1.0f : -1.0f),
					Animation.RELATIVE_TO_SELF, 0.0f);
			AlphaAnimation b = new AlphaAnimation(0.0f, 1.0f);
			RotateAnimation c = new RotateAnimation((mDown ? 25 : -25), 0, 0, 0);
			
			animation_set.addAnimation(a);
			animation_set.addAnimation(b);
			animation_set.addAnimation(c);
			
			anim = animation_set;
			break;
		case ANIMATION_GOOGLE_NOW_RIGHT:
			AnimationSet animation_set_2 = new AnimationSet(true);
			TranslateAnimation a2 = new TranslateAnimation(
					Animation.RELATIVE_TO_SELF, 0.0f,
					Animation.RELATIVE_TO_SELF, 0.0f,
					Animation.RELATIVE_TO_SELF, (mDown ? 1.0f : -1.0f),
					Animation.RELATIVE_TO_SELF, 0.0f);
			AlphaAnimation b2 = new AlphaAnimation(0.0f, 1.0f);
			RotateAnimation c2 = new RotateAnimation((mDown ? -25 : 25), 0, mWidth, 0);
			
			animation_set_2.addAnimation(a2);
			animation_set_2.addAnimation(b2);
			animation_set_2.addAnimation(c2);
			
			anim = animation_set_2;
			break;
		}
		anim.setDuration(mDuration);
		        	
		switch (mInterpolator) {
		case INTERPOLATOR_ACCELERATE:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.accelerate_interpolator));
			break;
		case INTERPOLATOR_DECELERATE:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.decelerate_interpolator));
			break;
		case INTERPOLATOR_ACCELERATE_DECELERATE:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.accelerate_decelerate_interpolator));
			break;
		case INTERPOLATOR_ANTICIPATE:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.anticipate_interpolator));
			break;
		case INTERPOLATOR_OVERSHOOT:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.overshoot_interpolator));
			break;
		case INTERPOLATOR_ANTICIPATE_OVERSHOOT:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.anticipate_overshoot_interpolator));
			break;
		case INTERPOLATOR_BOUNCE:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.bounce_interpolator));
			break;
		case INTERPOLATOR_CYCLE:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.cycle_interpolator));
			break;
		case INTERPOLATOR_LINEAR:
			anim.setInterpolator(AnimationUtils.loadInterpolator(mContext, android.R.anim.linear_interpolator));
			break;
		}
		view.startAnimation(anim);
		return view;
	}
}