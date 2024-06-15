#ifndef MASTER_THREAD_H
#define MASTER_THREAD_H

int thread_master(int argc, char* argv[],char *socket_name,volatile sig_atomic_t* stopflag,volatile sig_atomic_t* stampaflag,int i);

#endif
