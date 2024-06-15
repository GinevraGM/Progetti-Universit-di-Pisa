//#define _POSIX_C_SOURCE  200112L  // needed for S_ISSOCK

#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <signal.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>

#include <lista_ordinata.h>

ssize_t  /* Read "n" bytes from a descriptor */
readn(int fd, void *ptr, size_t n) {  
   size_t   nleft;
   ssize_t  nread;
 
   nleft = n;
   while (nleft > 0) {
     if((nread = read(fd, ptr, nleft)) < 0) {
        if (nleft == n) return -1; /* error, return -1 */
        else break; /* error, return amount read so far */
     } else if (nread == 0) break; /* EOF */
     nleft -= nread;
     ptr   += nread;
   }
   return(n - nleft); /* return >= 0 */
}

volatile sig_atomic_t sig_sigur2=0; //flag che mi dice se ho ricevuto i segnali SIGHUP, SIGINT, SIGQUIT, SIGTERM e quindi devo smettere di leggere i file da input ma finire di elborare quelli che ho gia in coda 

static void signals_handler_sigur2(int sig){
    
    write(1,"SIGUSR2 catturato\n",19);
    sig_sigur2=1;

}

int Collector(char * socket_name)
{
    int fd_skt; //file descriptor del socket creato dal server collector
    int fdc; // file descriptor della connessione socket creata tra collector e master worker
    
    sigset_t mask;
    sigemptyset(&mask);
    //Blocco i segnali che gestisce processo master
    if (sigaddset(&mask, SIGINT)!= 0) {
        perror("sigaddset SIGINT");
        return -1;
    }
    
    if (sigaddset(&mask, SIGQUIT)!= 0) {
        perror("sigaddset SIGQUIT");
        return -1;
    }

    if (sigaddset(&mask, SIGTERM)!= 0) {
        perror("sigaddset SIGTERM");
        return -1;
    }

    if (sigaddset(&mask, SIGHUP)!= 0) {
        perror("sigaddset SIGHUP");
        return -1;
    }

    if (sigaddset(&mask, SIGUSR1)!= 0) {
        perror("sigaddset SIGUSR1");
        return -1;
    }

    if (sigaddset(&mask, SIGUSR2)!= 0) {
        perror("sigaddset SIGUSR2");
        return -1;
    }

    if (pthread_sigmask(SIG_SETMASK, &mask,NULL) != 0) {
        fprintf(stderr, "FATAL ERROR, pthread_sigmask\n");
        return -1;
    }

    struct sigaction s;
    memset(&s,0,sizeof(s));    
    s.sa_handler=signals_handler_sigur2;

     if (sigdelset(&mask, SIGUSR2)!= 0) { //l'unico segnale che può sentire il Collector
        perror("sigaddset SIGUSR2");
        return -1;
    }

    if ( (sigaction(SIGUSR2,&s,NULL) ) == -1 ) {   
	    perror("sigaction SIGUSR2");
	    return -1;
    }

    if (pthread_sigmask(SIG_SETMASK, &mask,NULL) != 0) {
        fprintf(stderr, "FATAL ERROR, pthread_sigmask\n");
        return -1;
    }
    //la gestione di sigpipe la eredita

    struct sockaddr_un sa;
    strcpy(sa.sun_path, socket_name);
    sa.sun_family = AF_UNIX;

    if(sig_sigur2==1 || (fd_skt = socket(AF_UNIX, SOCK_STREAM, 0)) == -1){
        perror("socket");
        return -1;
    }

    if (sig_sigur2==1 || bind(fd_skt, (struct sockaddr *) &sa, sizeof(sa)) == -1) {
        perror("bind");
        close(fd_skt);
        return -1;
    }

    if (sig_sigur2==1 || listen(fd_skt, 1)==-1) {
        perror("listen");
        close(fd_skt);
        return -1;
    }

    if(sig_sigur2==1 || (fdc=accept(fd_skt, NULL, 0))==-1){
        perror("accept");
        close(fd_skt);
        return -1;
    }
    
    elem *head=NULL;
    //ora devo leggere dal socket con readn

    while(sig_sigur2==0){
        
        long result;
        char* data;
        int lenght_data;
        int eof;
        if(sig_sigur2==1 || (eof=readn(fdc, &lenght_data, sizeof(int)))==-1) //leggo la lunghezza della stringa filepath compresa di /0
        {
            perror("readn");
            close(fd_skt);
            if(head!=NULL)
            {
                libera_lista(head);
            }
            return -1;
        }

        if(eof==0) //è uguale a zero soltanto se non ho mai scritto nulla sul file socket, quindi la prima lettura legge subito EOF
        {//questo succede per esempio se ho avuto qualche problema e non sono riuscito a scrivere niente sul socket ma la kill per il collectore ancora non è arrivata 
            break;
        }

        data=malloc(sizeof(char)*lenght_data); 
        if (data==NULL) {
	        perror("collector malloc");
            close(fd_skt);
            if(head!=NULL)
            {
                libera_lista(head);
            }
	        return -1;
	    }

        if(sig_sigur2==1 || readn(fdc, data,sizeof(char)*lenght_data)==-1)
        {
            perror("readn");
            close(fd_skt);
            free(data);
            if(head!=NULL)
            {
                libera_lista(head);
            }
            return -1;
        }

        if(strncmp(data,"stampa", strlen("stampa")) == 0){ // se nel socket ho letto stampa significa che il processo master ha ricevuto SIGUSR1 e quindi bisogna fare una stampa parziale della lista 
            free(data);
            stampa_lista(head);
        }
        else
        {
            if(strncmp(data,"finish", strlen("finish")) == 0){ // se nel socket ho letto finish significa che tutti i worker hanno finito di lavorare, quindi posso finire di leggere dal socket
                free(data);
                break;
            }
            else
            {

                if(sig_sigur2==1 || readn(fdc, &result, sizeof(long))==-1) //ora che so che non è la fine leggo result 
                {
                    perror("readn");
                    close(fd_skt);
                    free(data);
                    if(head!=NULL)
                    {
                        libera_lista(head);
                    }
                    return -1;
                }

                // metto result e data in lista ordinata 
                head=inserisci_lista_ordinata(result,data,lenght_data,head); //lenghtdata contiene gia il +1 di /0
                if(sig_sigur2==1 || head==NULL) //head==NULL se ho avuto problemi con la malloc in inserisci_lista_ordinata
                {
                    close(fd_skt);
                    free(data);
                    if(head!=NULL)
                    {
                        libera_lista(head);
                    }
                    return -1;
                }
         
                free(data);
            }
        }
        
        
    }
    
    close(fd_skt);
    close(fdc);
    if(sig_sigur2==0) 
    {
       stampa_lista(head);             
    }
    libera_lista(head);
    return 0;
}

