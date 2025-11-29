#include "libsysy.h"

//#include <stdio.h>
//
//int getchar()
//{
//    char c;
//    scanf("%c", &c);
//    return (int)c;
//}
//
//int getint()
//{
//    int t;
//    scanf("%d", &t);
//    while (getchar() != '\n')
//        continue;
//    return t;
//}
//
//int getarray(int a[])
//{
//    int n;
//    scanf("%d", &n);
//    for (int i = 0; i < n; i++)
//        scanf("%d", &a[i]);
//    return n;
//}
//
//void putint(int a) { printf("%d", a); }
//
//void putch(int a) { printf("%c", a); }
//
//void putarray(int n, int a[])
//{
//    printf("%d:", n);
//    for (int i = 0; i < n; i++)
//        printf(" %d", a[i]);
//    printf("\n");
//}
//
//void putstr(char *str)
//{
//    printf("%s", str);
//}

extern int getchar(void);
extern int putchar(int);

void putch(int a) {
    putchar(a);
}

void putint(int x) {
    if (x == 0) {
        putchar('0');
        return;
    }

    if (x < 0) {
        putchar('-');
        x = -x;
    }

    char buf[20];
    int idx = 0;

    while (x > 0) {
        buf[idx++] = (x % 10) + '0';
        x /= 10;
    }

    while (idx--) {
        putchar(buf[idx]);
    }
}

int getint() {
    int x = 0, f = 1;
    int c = getchar();

    while (c == ' ' || c == '\n' || c == '\t' || c == '\r')
        c = getchar();

    if (c == '-') {
        f = -1;
        c = getchar();
    }

    while (c >= '0' && c <= '9') {
        x = x * 10 + (c - '0');
        c = getchar();
    }

    return f * x;
}

void putstr(char *str) {
    while (*str) {
        putchar(*str++);
    }
}
