# UFA updater
LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := UbuntuInstaller
LOCAL_CERTIFICATE := platform
LOCAL_SDK_VERSION := current

LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_PACKAGE)
