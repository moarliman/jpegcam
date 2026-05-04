/*
 * Android 2.3.7 (API 10) compatibility stubs.
 *
 * NDK r27's libc++ runtime references symbols added after API 10. By defining
 * them here with hidden visibility, the static linker resolves libc++'s
 * references at link time. The symbols never appear as UND in the .so's
 * dynamic symbol table, so System.loadLibrary() succeeds on the camera.
 */

#include <stdarg.h>
#include <stdlib.h>
#include <errno.h>
#include <malloc.h>

/* android_set_abort_message: added in API 21 */
__attribute__((visibility("hidden")))
void android_set_abort_message(const char* msg) {
    (void)msg;
}

/* posix_memalign: added in API 17.
   memalign() is available on API 10 and memory is free()-able. */
__attribute__((visibility("hidden")))
int posix_memalign(void** memptr, size_t alignment, size_t size) {
    if (!alignment || (alignment & (alignment - 1))) return EINVAL;
    void* ptr = memalign(alignment, size);
    if (!ptr) return ENOMEM;
    *memptr = ptr;
    return 0;
}

/* syslog functions: added in API 23 */
__attribute__((visibility("hidden")))
void openlog(const char* ident, int option, int facility) {
    (void)ident; (void)option; (void)facility;
}

__attribute__((visibility("hidden")))
void syslog(int priority, const char* message, ...) {
    (void)priority; (void)message;
}

__attribute__((visibility("hidden")))
void closelog(void) {}
