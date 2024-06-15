#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <sys/types.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>


#include <boundedqueue.h>

pthread_mutex_t mtx_socket = PTHREAD_MUTEX_INITIALIZER;

typedef struct threadArgs { //struttura dati per i thread consumatori, serve a passare gli arg ai thread che andro a creare
    int      thid;
    BQueue_t *q;
    int fd_skt;
    volatile sig_atomic_t* stampaflag;
} threadArgs_t;

ssize_t  /* Write "n" bytes to a descriptor */
writen1(int fd, void *ptr, size_t n) {  
   size_t   nleft;
   ssize_t  nwritten;
 
   nleft = n;
   while (nleft > 0) {
     if((nwritten = write(fd, ptr, nleft)) < 0) {
        if (nleft == n) return -1; /* error, return -1 */
        else break; /* error, return amount written so far */
     } else if (nwritten == 0) break; 
     nleft -= nwritten;
     ptr   += nwritten;
   }
   return(n - nleft); /* return >= 0 */
}

long calcolo_su_file(char *data)
{
    FILE *ifp;
    int i;
    long result=0; 

    if((ifp=fopen(data, "rb")) == NULL){//rb perche usiamo file binari 
        perror("fopen");
        return -1;
    }

    if((fseek(ifp, 0L, SEEK_END)) != 0){ // mi porto alla fine del file
        perror("fseek");
        fclose(ifp);
        return -1;
    }

    int file_size =ftell(ifp); //mi restituisce quanti byte ci sono dallinizio del file 
    if(file_size==-1)
    {
        perror("ftell");
        fclose(ifp);
        return -1;
    }
    //fino alla posizione corrente (la posizione corrente è la fine del file, raggiunta in seguito dalla fseek)
    int n_long_in_file = file_size/8; // i long sono codificati in 8 bytes

    rewind(ifp);// torno allinizio del file per fare la fread

    long* ptr_vector=malloc(sizeof(long)*n_long_in_file); //vettore che usera la fread per memorizzare i long che legge dal file

    if(ptr_vector==NULL)
    {
        perror("malloc");
        fclose(ifp);
        return -1;
    }
    errno=0;
    fread(ptr_vector,sizeof(long),n_long_in_file,ifp);
    if(errno!=0)
    {
        perror("fread");
        fclose(ifp);
        free(ptr_vector);
        return -1;
    }

    for(i=0;i<n_long_in_file;i++){
        result=result+(i*ptr_vector[i]);
    }

    fclose(ifp);
    free(ptr_vector);

    return result;

}

void *Worker_consumer(void *arg) {
    BQueue_t *q = ((threadArgs_t*)arg)->q;
    int   myid  = ((threadArgs_t*)arg)->thid;
    int fd_skt=((threadArgs_t*)arg)->fd_skt;
    volatile sig_atomic_t* stampaflag=((threadArgs_t*)arg)->stampaflag;

    sigset_t mask;
    sigemptyset(&mask);
    //Blocco tutti i segnali che gestisce il master

    if (sigaddset(&mask, SIGINT)!= 0) {
        perror("sigaddset SIGINT");
        pthread_exit((void *)-1);
    }
    
    if (sigaddset(&mask, SIGQUIT)!= 0) {
        perror("sigaddset SIGQUIT");
        pthread_exit((void *)-1);
    }

    if (sigaddset(&mask, SIGTERM)!= 0) {
        perror("sigaddset SIGTERM");
        pthread_exit((void *)-1);
    }

    if (sigaddset(&mask, SIGHUP)!= 0) {
        perror("sigaddset SIGHUP");
        pthread_exit((void *)-1);
    }

    if (sigaddset(&mask, SIGUSR1)!= 0) {
        perror("sigaddset SIGUSR1");
        pthread_exit((void *)-1);
    }

    if (sigaddset(&mask, SIGUSR2)!= 0) {
        perror("sigaddset SIGUSR2");
        pthread_exit((void *)-1);
    }

     if (sigaddset(&mask, SIGPIPE)!= 0) {
        perror("sigaddset SIGUSR2");
        pthread_exit((void *)-1);
    }

    if (pthread_sigmask(SIG_SETMASK, &mask,NULL) != 0) {
        fprintf(stderr, "FATAL ERROR, pthread_sigmask\n");
        pthread_exit((void *)-1);
    }

    while(1)
    {
        char *data=NULL;
        long result=0;
        data=pop(q); //prelevo il puntatore a data
        if(data==NULL)
        {
            fprintf(stderr, "pop fallita\n");
            pthread_exit((void *)EXIT_FAILURE);

        }
        
        if (strncmp(data, "finish", strlen("finish")) == 0)
        {
            break;
        }

        result=calcolo_su_file(data);

        if(result==-1)
        {
            fprintf(stderr, "errore nel calcolo del risultato\n");
            free(data);	
            pthread_exit((void *)EXIT_FAILURE);	
        }

        if (pthread_mutex_lock(&mtx_socket)!=0)        
        { 
            fprintf(stderr, "ERRORE FATALE lock\n");
            free(data);	    
            pthread_exit((void *)EXIT_FAILURE);			    
        }   

        
        if(*stampaflag==1) //questo pezzo di codice viene eseguito da un thread alla volta (potrei avere concorrenza tra thread e master worker che setta il flag,
        //comunque il flag settato non verra magari letto da questo thread ma sicuramente da quello dopo)
        {
            char *stampa="stampa";
            int length_data=strlen("stampa")+1;
            if (writen1(fd_skt, &length_data, sizeof(int))==-1) {// scrivo la lunghezza (con il carattere terminatore /0 )della stringa stampa
                fprintf(stderr, "Error writen1 \n");
                free(data);
                pthread_mutex_unlock(&mtx_socket);
                pthread_exit((void *)EXIT_FAILURE);
            }

            if (writen1(fd_skt,stampa,sizeof(char)*(length_data))==-1) {  //scrivo stampa con il terminatore /0
                fprintf(stderr, "Error writen1 \n");
                free(data);
                pthread_mutex_unlock(&mtx_socket);
                pthread_exit((void *)EXIT_FAILURE);
            }

            *stampaflag=0;
        }


        int length_data= (int)strlen(data)+1; //+1 per il carattere /0
        if (writen1(fd_skt, &length_data, sizeof(int))==-1) {  // scrivo la lunghezza del path
            fprintf(stderr, "Error writen1 \n");
            free(data);
            pthread_mutex_unlock(&mtx_socket);
            pthread_exit((void *)EXIT_FAILURE);
        }

        if (writen1(fd_skt,data,sizeof(char)*length_data)==-1) {//scrivo il path
            fprintf(stderr, "Error writen1 \n");
            free(data);
            pthread_mutex_unlock(&mtx_socket);
            pthread_exit((void *)EXIT_FAILURE);
        }
        if (writen1(fd_skt, &result, sizeof(long))==-1) {// scrivo il risultato
            fprintf(stderr, "Error writen1 \n");
            free(data);
            pthread_mutex_unlock(&mtx_socket);
            pthread_exit((void *)EXIT_FAILURE);
        }

        if (pthread_mutex_unlock(&mtx_socket)!=0)
        { 
            fprintf(stderr, "ERRORE FATALE unlock\n");	
            free(data);	    
            pthread_exit((void *)EXIT_FAILURE);				    
        }

        free(data);
    }
    pthread_exit((void *)0);
	
}
