/*******************************************************************************
 *  _  _ ___ ___     _ _
 * | \| | __/ __| __| | |__
 * | .` | _|\__ \/ _` | '_ \
 * |_|\_|_| |___/\__,_|_.__/
 *
 * Copyright (c) 2014-2016. The NFSdb project and its contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

#include <unistd.h>
#include <sys/stat.h>
#include <sys/fcntl.h>
#include <sys/time.h>
#include <utime.h>
#include "files.h"

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_write
        (JNIEnv *e, jclass cl,
         jlong fd,
         jlong address,
         jint len,
         jlong offset) {
    return pwrite((int) fd, (void *) (address), (size_t) len, (off_t) offset);
}

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_append
        (JNIEnv *e, jclass cl,
         jlong fd,
         jlong address,
         jint len) {
    return write((int) fd, (void *) (address), (size_t) len);
}

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_read
        (JNIEnv *e, jclass cl,
         jlong fd,
         jlong address,
         jint len,
         jlong offset) {

    return pread((int) fd, (void *) address, (size_t) len, (off_t) offset);
}

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_getLastModified
        (JNIEnv *e, jclass cl, jlong pchar) {

    struct stat st;

    int r = stat((const char *) pchar, &st);
    return r == 0 ? 1000 * (jlong) st.st_mtime : r;
}

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_openRO
        (JNIEnv *e, jclass cl, jlong lpszName) {
    return open((const char *) lpszName, O_RDONLY);
}

JNIEXPORT jint JNICALL Java_com_nfsdb_misc_Files_close
        (JNIEnv *e, jclass cl, jlong fd) {
    return close((int) fd);
}

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_openRW
        (JNIEnv *e, jclass cl, jlong lpszName) {
    umask(0);
    return open((const char *) lpszName, O_CREAT | O_RDWR, 0644);
}

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_openAppend
        (JNIEnv *e, jclass cl, jlong lpszName) {
    umask(0);
    return open((const char *) lpszName, O_CREAT | O_WRONLY | O_APPEND, 0644);
}

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_length
        (JNIEnv *e, jclass cl, jlong pchar) {
    struct stat st;

    int r = stat((const char *) pchar, &st);
    return r == 0 ? st.st_size : r;
}

#ifdef __APPLE__

JNIEXPORT jboolean JNICALL Java_com_nfsdb_misc_Files_setLastModified
        (JNIEnv *e, jclass cl, jlong lpszName, jlong millis) {
    struct timeval t[2];
    t[1].tv_sec = millis / 1000;
    t[1].tv_usec = (__darwin_suseconds_t) ((millis % 1000) * 1000);
    return (jboolean) (utimes((const char *) lpszName, t) == 0);
}

#else

JNIEXPORT jboolean JNICALL Java_com_nfsdb_misc_Files_setLastModified
        (JNIEnv *e, jclass cl, jlong lpszName, jlong millis) {
    struct utimbuf t;
    t.modtime = millis/1000;
    return (jboolean) (utime((const char *) lpszName, &t) == 0);
}

#endif

JNIEXPORT jlong JNICALL Java_com_nfsdb_misc_Files_getStdOutFd
        (JNIEnv *e, jclass cl) {
    return (jlong) 1;
}