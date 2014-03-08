# overall Android.mk for Location Sensor service package
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_PACKAGE_NAME := LocationSensor

LOCAL_ROOT_DIR := $(shell (cd $(LOCAL_PATH); pwd))

LOCAL_SRC_FILES := \
		$(call all-java-files-under, src) \
		$(call all-Iaidl-files-under, aidl) 

LOCAL_AIDL_INCLUDES += \
    $(LOCAL_ROOT_DIR)/aidl


# List of static libraries to include in the package
LOCAL_STATIC_JAVA_LIBRARIES := ls-vsms ls-fbsdk ls-signpost-common ls-signpost-core

LOCAL_CERTIFICATE := platform
#LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/res

include $(BUILD_PACKAGE)

##################################################
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := ls-vsms:libs/VSMS.jar ls-fbsdk:libs/fbsdk.jar ls-signpost-common:libs/signpost-commonshttp4-1.2.1.1.jar ls-signpost-core:libs/signpost-core-1.2.1.1.jar

include $(BUILD_MULTI_PREBUILT)

##include $(call all-makefiles-under,$(LOCAL_PATH))
#include $(LOCAL_PATH)/jni/Android.mk
