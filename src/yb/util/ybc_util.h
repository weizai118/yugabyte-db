// Copyright (c) YugaByte, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the License
// is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
// or implied.  See the License for the specific language governing permissions and limitations
// under the License.

// C wrappers around some YB utilities. Suitable for inclusion into C codebases such as our modified
// version of PostgreSQL.

#ifndef YB_UTIL_YBC_UTIL_H
#define YB_UTIL_YBC_UTIL_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {

struct varlena;

#endif

typedef struct YBCStatusStruct {
  int code;
  char msg[0];
} YBCStatusStruct;

typedef struct YBCStatusStruct* YBCStatus;

extern YBCStatus YBCStatusOK;
bool YBCStatusIsOK(YBCStatus s);
bool YBCStatusIsNotFound(YBCStatus s);
void YBCFreeStatus(YBCStatus s);

#define CHECKED_YBCSTATUS __attribute__ ((warn_unused_result)) YBCStatus

typedef void* (*YBCPAllocFn)(size_t size);

typedef struct varlena* (*YBCCStringToTextWithLenFn)(const char* c, int size);

// Global initialization of the YugaByte subsystem.
CHECKED_YBCSTATUS YBCInit(
    const char* argv0,
    YBCPAllocFn palloc_fn,
    YBCCStringToTextWithLenFn cstring_to_text_with_len_fn);

// Logging functions with printf-like formatting capabilities.
void YBCLogInfo(const char* format, ...);
void YBCLogWarning(const char* format, ...);
void YBCLogError(const char* format, ...);
void YBCLogFatal(const char* format, ...);

// The following functions log the given message formatted similarly to printf followed by a stack
// trace.
void YBCLogInfoStackTrace(const char* format, ...);
void YBCLogWarningStackTrace(const char* format, ...);
void YBCLogErrorStackTrace(const char* format, ...);

#ifdef __cplusplus
}
#endif

#endif  // YB_UTIL_YBC_UTIL_H
