/*
 * Copyright (C) 2013 XuiMod
 * Based on source code from AOKP by Steve Spear - Stevespear426, Copyright (C) 2013
 * Contains portions from The Android Open Source Project, Copyright (C) 2011
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

/**
 * Based on commits by AOKP:
 * https://github.com/AOKP/frameworks_base/commit/05ade0eb4ddb7946b315b947c460bd749daea79d
 * https://github.com/AOKP/packages_apps_ROMControl/commit/d106dda0438885e36555cb5549dd0b289f5a85ec
 * https://github.com/AOKP/frameworks_base/commit/015c9cf00a8e49aea22d70b57b0af87c9134be73
 */

package com.zst.xposed.xuimod.mods;

import static de.robv.android.xposed.XposedHelpers.findClass;

import com.zst.xposed.xuimod.Common;
import com.zst.xposed.xuimod.XuiMod;
import com.zst.xposed.xuimod.mods.animation.AppTransitionConstants;
import com.zst.xposed.xuimod.mods.animation.AwesomeAnimationHelper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.XModuleResources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class AnimationControlsMod {
	
	private static XSharedPreferences mPref;
	private static Context mContext;

	private static boolean mIsResId;
	private static boolean mEnabled;
	private static boolean mNoOverrides; //prevent overridePendingTransition
	private static int mAnimationDuration;
	private static int[] mActivityAnimations = new int[10];
	
	public static void initZygote(){
		mPref = new XSharedPreferences(Common.MY_PACKAGE_NAME, Common.ANIM_CONTROLS_PREFERENCE_FILENAME);
		updateSettings();
	}
	
	public static void handleLoadPackage(LoadPackageParam lpparam){
		mPref.reload();
		Class<?> appTransition;
		try{
			if (Build.VERSION.SDK_INT >= 18) {
				//Android 4.3 onwards
				appTransition = findClass("com.android.server.wm.AppTransition",
						lpparam.classLoader);
			} else {
				//Android 4.2 and below
				appTransition = findClass("com.android.server.wm.WindowManagerService",
						lpparam.classLoader);
			}
		}catch(Throwable e){
			return;
			//If can't find the class, return so other mods executed after this
			//wont be affected.
		}
		hookNoOverrideReturnMethods(lpparam, appTransition);
		hookConstructor(lpparam, appTransition);
		hookloadAnimation_animAttr(lpparam, appTransition);
		hookloadAnimation_mIsResId_mNextAppTransitionType(lpparam, appTransition);
	}
	
	/** Check for mNoOverrides. If true, return to prevent code execution. */
	private static void hookNoOverrideReturnMethods(LoadPackageParam o, Class<?> hookClass) {
		final XC_MethodHook checkNoOverrideAndReturn = new XC_MethodHook(){
			@Override 
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				if (mNoOverrides) 
					param.setResult(null);
			}
		};
		try {
			XposedBridge.hookAllMethods(hookClass, "overridePendingAppTransitionScaleUp", checkNoOverrideAndReturn);
			XposedBridge.hookAllMethods(hookClass, "overridePendingAppTransitionThumb", checkNoOverrideAndReturn);
		} catch (Throwable t){
			// Method not found, user is on ICS
		}
	    XposedBridge.hookAllMethods(hookClass, "overridePendingAppTransition", checkNoOverrideAndReturn);
	}
	
	private static void hookConstructor(final LoadPackageParam o, Class<?> hookClass) {
	    XposedBridge.hookAllConstructors(hookClass, new XC_MethodHook(){
			@Override 
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				mContext = (Context) param.args[0];
				IntentFilter filter = new IntentFilter();
    			filter.addAction(Common.ACTION_SETTINGS_CHANGED);
    			mContext.registerReceiver(broadcastReceiver, filter);
			}
	    });
	}
	
	private static void hookloadAnimation_animAttr(LoadPackageParam o, Class<?> clazz) {
		try {
			XposedHelpers.findAndHookMethod(clazz, "loadAnimation",
					WindowManager.LayoutParams.class, int.class, new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					int animAttr = (Integer) param.args[1];
					if (animAttr >= 0) {
						if (!mIsResId) return;
						
						if (animAttr != 0) {
							param.setResult(AnimationUtils
									.loadAnimation(mContext, animAttr));
						} else {
							param.setResult(null);
						}
						/* If it equals zero, return null and skip the method since we have
						 * to skip the getCachedAnimations if mIsResId is true
						 */
					}
				}
			});
		} catch (Throwable e) {
			XposedBridge.hookAllMethods(clazz, "loadAnimation", new XC_MethodHook() {
				@Override
				protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
					if (param.args.length != 2) return;
					if (param.args[0] instanceof WindowManager.LayoutParams
							&& param.args[1] instanceof Integer) {
						int animAttr = (Integer) param.args[1];
						if (animAttr >= 0) {
							if (!mIsResId) return;
							if (animAttr != 0) {
								param.setResult(AnimationUtils.loadAnimation(mContext, animAttr));
							} else {
								param.setResult(null);
							}
						}
					}
				}
			});
		}
	}
	
	// Checks API level
	private static void hookloadAnimation_mIsResId_mNextAppTransitionType(LoadPackageParam o, Class<?> cls) {
		if (Build.VERSION.SDK_INT >= 18) {
			hookNewLoadAnimation(o, cls);
		} else {
			hookOldApplyAnimationLocked(o, cls);
		}
	}
	
	// Android 4.2 and below
	private static void hookOldApplyAnimationLocked(LoadPackageParam o, final Class<?> cls) {
		Class<?> appWindowToken = findClass("com.android.server.wm.AppWindowToken", o.classLoader);
		XposedHelpers.findAndHookMethod(cls, "applyAnimationLocked", appWindowToken,
				WindowManager.LayoutParams.class, int.class, boolean.class, new XC_MethodHook() {
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				boolean displayFrozen = XposedHelpers.getBooleanField(param.thisObject, "mDisplayFrozen");
				boolean displayEnabled = XposedHelpers.getBooleanField(param.thisObject, "mDisplayEnabled");
				Object policy = XposedHelpers.getObjectField(param.thisObject, "mPolicy");
				boolean screenOn = (Boolean) XposedHelpers.callMethod(policy, "isScreenOnFully");
				
				if ((!displayFrozen && displayEnabled && screenOn) == false) {
					return;
				}
				
				final Object window_token = param.args[0];
				final WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.args[1];
				final int transit = (Integer) param.args[2];
				final boolean enter = (Boolean) param.args[3];
				
				if ((Build.VERSION.SDK_INT <= 15)) { // ICS
					String nextAppTransitionPackage = (String) XposedHelpers
							.getObjectField(param.thisObject, "mNextAppTransitionPackage");
					if (nextAppTransitionPackage == null) {
						Animation anim = retrieveAnimation(lp, transit, enter);
						if (anim != null) {
							XposedHelpers.callMethod(window_token, "setAnimation", applyDuration(anim));
							param.setResult(true);
						}
					}
				} else { // JB 4.1/4.2
					int type = XposedHelpers.getIntField(param.thisObject,
							"mNextAppTransitionType");
					if (type == AppTransitionConstants.NEXT_TRANSIT_TYPE_CUSTOM ||
						type == AppTransitionConstants.NEXT_TRANSIT_TYPE_SCALE_UP ||
						type == AppTransitionConstants.NEXT_TRANSIT_TYPE_SCALE_UP)
						return;
					Animation result = retrieveAnimation(lp, transit, enter);
					Object appAnimator = XposedHelpers.getObjectField(window_token, "mAppAnimator");
					if (result != null) {
						XposedHelpers.callMethod(appAnimator, "setAnimation",
								applyDuration(result), false);
						param.setResult(true);
					}
				}
			}
			
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				// To set duration for default animation also.
				Object animator;
				if ((Build.VERSION.SDK_INT <= 15)) { // ICS
					animator = param.args[0];
				} else { // JB
					final Object window_token = param.args[0];
					animator = XposedHelpers.getObjectField(window_token, "mAppAnimator");
				}
				Animation anim = (Animation) XposedHelpers.getObjectField(animator, "animation");
				if (anim != null) {
					if ((Build.VERSION.SDK_INT <= 15)) {
						XposedHelpers.callMethod(animator, "setAnimation", applyDuration(anim));
					} else { // JB
						XposedHelpers.callMethod(animator, "setAnimation", applyDuration(anim), false);
					}
					param.setResult(true);
				}
			}
		});
	}
	
	// Android 4.3 onwards only
	private static void hookNewLoadAnimation(LoadPackageParam o, Class<?> cls) {
		XposedHelpers.findAndHookMethod(cls, "loadAnimation", WindowManager.LayoutParams.class,
				int.class, boolean.class, int.class, int.class, new XC_MethodHook(){
			@Override 
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				mIsResId = false;
				
				int type = XposedHelpers.getIntField(param.thisObject,
						"mNextAppTransitionType");
				if (type == AppTransitionConstants.NEXT_TRANSIT_TYPE_CUSTOM ||
					type == AppTransitionConstants.NEXT_TRANSIT_TYPE_SCALE_UP ||
					type == AppTransitionConstants.NEXT_TRANSIT_TYPE_SCALE_UP) return; 
				
				WindowManager.LayoutParams lp = (WindowManager.LayoutParams) param.args[0];
				int transit = (Integer) param.args[1];
				boolean enter = (Boolean) param.args[2];
				
				Animation result = retrieveAnimation(lp, transit, enter);
				if (result != null){
					param.setResult(applyDuration(result));
				}
				//else result == null (means no animation replacement is needed)
				//Don't setResult for original method to init the default animation
			}
			@Override 
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				param.setResult(applyDuration((Animation)param.getResult()));
			}
		});
	}
    
    private static Animation retrieveAnimation(WindowManager.LayoutParams lp, int transit, boolean enter){
    	int animAttr = 0;
    	int[] animArray = { 0, 0 };
    	int arrayNumber = -1;
    	
        switch (transit) {
            case AppTransitionConstants.TRANSIT_ACTIVITY_OPEN:
            	arrayNumber = 0;
                break;
            case AppTransitionConstants.TRANSIT_ACTIVITY_CLOSE:
            	arrayNumber = 1;
                break;
            case AppTransitionConstants.TRANSIT_TASK_OPEN:
            	arrayNumber = 2;
                break;
            case AppTransitionConstants.TRANSIT_TASK_CLOSE:
            	arrayNumber = 3;
                break;
            case AppTransitionConstants.TRANSIT_TASK_TO_FRONT:
            	arrayNumber = 4;
                break;
            case AppTransitionConstants.TRANSIT_TASK_TO_BACK:
            	arrayNumber = 5;
                break;
            case AppTransitionConstants.TRANSIT_WALLPAPER_OPEN:
            	arrayNumber = 6;
                break;
            case AppTransitionConstants.TRANSIT_WALLPAPER_CLOSE:
            	arrayNumber = 7;
                break;
            case AppTransitionConstants.TRANSIT_WALLPAPER_INTRA_OPEN:
            	arrayNumber = 8;
                break;
            case AppTransitionConstants.TRANSIT_WALLPAPER_INTRA_CLOSE:
            	arrayNumber = 9;
                break;
        }
        
        if (arrayNumber == -1) {
        	return null;
        }
        
        if (mActivityAnimations[arrayNumber] != 0) {
            mIsResId = true;
            animArray = AwesomeAnimationHelper.getAnimations(mActivityAnimations[arrayNumber]);
        }
        
        animAttr = enter
                ? animArray[1]
                : animArray[0];
        Animation anim = null;
        if (animAttr != 0) {
    		XModuleResources modRes = XModuleResources.createInstance(XuiMod.MODULE_PATH, null);
    		XmlResourceParser parser = modRes.getAnimation(animAttr);
        	anim = (Animation)XposedHelpers.callStaticMethod(AnimationUtils.class, "createAnimationFromXml", mContext, parser) ;
        	/** Creating XML from a parser instead of res ID is hidden in APIs. Using reflection */
        }
        mIsResId = false;
        return anim;
    }

	/** Set duration only if it is positive and not -1 */
	private static Animation applyDuration(Animation a){
		if (a != null){
			if (mAnimationDuration >= 0) {
				a.setDuration(mAnimationDuration);
			}
		}
		return a;
	}
	
	private final static BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			mPref.reload();
			updateSettings();
		}
	};
	
	private static void updateSettings(){		
		mEnabled = mPref.getBoolean(Common.KEY_ANIMATION_CONTROLS_ENABLE, Common.DEFAULT_ANIMATION_CONTROLS_ENABLE);
		/** Return so we do not init unnecessary prefs */
		mNoOverrides = mPref.getBoolean(Common.KEY_ANIMATION_CONTROLS_NO_OVERRIDE,
				Common.DEFAULT_ANIMATION_CONTROLS_NO_OVERRIDE);
		mAnimationDuration = mPref.getInt(Common.KEY_ANIMATION_CONTROLS_DURATION,
				Common.DEFAULT_ANIMATION_CONTROLS_DURATION);	
		
		if (!mEnabled) {
			mActivityAnimations = new int[10]; 
			/** Reset Animations to system default.*/
			return; 
		}
		for (int i = 0; i < Common.KEYS_ANIMATION_CONTROLS_ACTIVITY.length; i++) {
			String numb = mPref.getString (Common.KEYS_ANIMATION_CONTROLS_ACTIVITY[i],
					Common.DEFAULT_ANIMATION_CONTROLS_ACTIVITY);
			mActivityAnimations[i] = Integer.valueOf(numb);
		}
		

	}
}