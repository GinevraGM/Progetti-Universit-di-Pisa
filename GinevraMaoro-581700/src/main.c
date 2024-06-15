#include <unistd.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>

#include <Master_thread.h>
#include <Collector.h>

#define SOCKETNAME "./farm.sck"


volatile sig_atomic_t sig_stampa_flag=0; //flag che mi dice se ho ricevuto segnle SIGUSR1 e quindi stampare i file che il collector ha ricevuto fino a quel momento
volatile sig_atomic_t sig_stop_flag=0; //flag che mi dice se ho ricevuto i segnali SIGHUP, SIGINT, SIGQUIT, SIGTERM e quindi devo smettere di leggere i file da input ma finire di alborare quelli che ho gia in coda 

static void signals_handler_sigur1(int sig){
    
    sig_stampa_flag = 1;
    write(1,"SIGUSR1 catturato\n",19);

}

static void signals_handler_other(int sig){

    sig_stop_flag = 1;
    write(1,"SIGHUP|SIGINT|SIGQUIT|SIGTERM catturato\n",41);
    
}


int main(int argc, char* argv[]) {

    sigset_t mask;
    sigemptyset(&mask);
    //Blocco inizialmente i segnali da gestire
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

    if (sigaddset(&mask, SIGPIPE)!= 0) {
        perror("sigaddset SIGPIPE");
        return -1;
    }

    if (sigaddset(&mask, SIGUSR1)!= 0) {
        perror("sigaddset SIGUSR1");
        return -1;
    }

    if (pthread_sigmask(SIG_SETMASK, &mask,NULL) != 0) {
        fprintf(stderr, "FATAL ERROR, pthread_sigmask\n");
        perror("pthread_sigmask");
        return -1;
    }

    //ora installo il signal handler per SIGPIPE:voglio che venga ignorato da entrambi i processi(masterworker e collector)
    struct sigaction s;
    memset(&s,0,sizeof(s));  
    s.sa_handler=SIG_IGN;

    if ( (sigaction(SIGPIPE,&s,NULL) ) == -1 ) {   
	    perror("sigaction");
	    return -1;
    } 
    //qui i segnali sono ancora mascherati
    //installo i signal handler per gli altri segnali per il processo masterworker
    s.sa_handler = signals_handler_sigur1;

    if (sigaction(SIGUSR1,&s,NULL)==-1) {
        perror("sigaction");
        return -1;
    }
    
    s.sa_handler = signals_handler_other;

    if (sigaction(SIGHUP,&s,NULL)==-1) {
        perror("sigaction");
        return -1;
    }

    if (sigaction(SIGINT,&s,NULL)==-1) {
        perror("sigaction");
        return -1;
    }

    if (sigaction(SIGQUIT,&s,NULL)==-1) {
        perror("sigaction");
        return -1;
    }

    if (sigaction(SIGTERM,&s,NULL)==-1) {
        perror("sigaction");
        return -1;
    }

    // smaschero i segnali che voglio gestire
    if (sigemptyset(&mask)==-1) {
        perror("sigemptyset");
        return -1;
    }
    
    if (pthread_sigmask(SIG_SETMASK, &mask,NULL) != 0) {
        fprintf(stderr, "FATAL ERROR, pthread_sigmask\n");
        perror("pthread_sigmask");
        return -1;
    }

    unlink(SOCKETNAME);

    if(argc == 1){ // almeno un argomento cioe il nome del file 
        fprintf(stderr, "Usa %s -n <nthread> -q <qlen> (-d <directory-name>) list_of_files -t <delay>\n", argv[0]);
        fprintf(stderr, "Altrimenti usa %s (-d <directory-name>) list_of_files\n", argv[0]);
        return -1;
    }
    
    int pid = fork();

    if(pid == -1){
        perror("fork");
        exit(EXIT_FAILURE);
    }
    else 
    {
        if(pid == 0)//processo figlio: collector, con la fork il figlio eredita la gestione dei segnali e la signal mask del padre
        { 
            fprintf(stdout, "Processo figlio %d\n", getpid());
            int error=Collector(SOCKETNAME);
            if(error==0){
                fprintf(stdout, "Processo figlio %d terminato con successo\n", getpid());
                exit(EXIT_SUCCESS);
            }else{
                fprintf(stdout, "Processo figlio %d terminato con fallimento\n", getpid());
                exit(EXIT_FAILURE);
            }

        }
        else // processo padre: MasterWorker, pid contiene quindi id del figlio
        { 
            fprintf(stdout, "Processo padre %d\n", getpid());

            volatile sig_atomic_t* stopflag=&sig_stop_flag;
            volatile sig_atomic_t* stampaflag=&sig_stampa_flag; 
    
            int error=thread_master(argc,argv,SOCKETNAME,stopflag,stampaflag,pid);
            if(error==0){
                fprintf(stdout, "Processo padre %d terminato con successo\n", getpid());
                if((waitpid(pid, NULL, 0))==-1)
                {
                    perror("waitpid:");
                    exit(EXIT_FAILURE);
                } 
                exit(EXIT_SUCCESS);
            }
            else
            {
                fprintf(stdout, "Processo padre %d terminato con fallimento\n", getpid());
                kill(pid, SIGUSR2); // il processo padre è terminato con fallimento mando quindi il segnale SIGUR2 al collector per chiuderlo
                if((waitpid(pid, NULL, 0))==-1)
                {
                    perror("waitpid:");
                    fprintf(stdout, "waitpid fallita\n");
                    exit(EXIT_FAILURE);
                } 
                exit(EXIT_FAILURE);
            }
        
        }
    }

}



