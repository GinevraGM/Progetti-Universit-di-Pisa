#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <getopt.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <signal.h>

#include <boundedqueue.h>
#include <Worker_consumer.h>

#define DEFAULT_N_WORKER 4
#define DEFAULT_LEN_QUEUE 8
#define DEFAULT_DELAY 0
#define MAX_LEN_FILE_NAME 255


typedef struct threadArgs { //struttura dati per i thread consumatori, serve a passare gli arg ai thread che andrò a creare
    int      thid;
    BQueue_t *q;
    int fd_skt;
    volatile sig_atomic_t* stampaflag;
} threadArgs_t;

ssize_t  /* Write "n" bytes to a descriptor */
writen(int fd, void *ptr, size_t n) {  
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


int inserisci(char *nameORpath, BQueue_t *q ) {

    char *data=malloc(sizeof(char)*MAX_LEN_FILE_NAME); 
    if (data==NULL) {
	    perror("Producer malloc");
	    return -1;
	}
    strcpy(data,nameORpath);
	if (push(q, data) == -1) {
	    fprintf(stderr, "Errore: push\n");
        return -1;
	}
    return 0;
}


long isNumber(const char* s){
    char* e = NULL;
    long val = strtol(s, &e, 10);
    if(e != NULL && *e == (char)0) return val; //val contiene la conversione di char a long 
    return -1; // non era un numero o ho avuto altri problemi
}

int controllo_file_regolare(const char* filename){
    struct stat statbuf;

    if(stat(filename, &statbuf) == -1){
        perror("eseguendo la stat in controllo file\n");
		fprintf(stderr,"Errore nel file %s\n", filename);
        return -1; // non riesco a leggere i metadati del file
    }

    if(S_ISREG(statbuf.st_mode)) {return 0;} //è un file regolare
    else return -1;
}

int controllo_directory(const char* nome_dir){
    struct stat statbuf;

    if(stat(nome_dir, &statbuf) == -1){
        perror("eseguendo la stat in controllo dir\n");
		fprintf(stderr,"Errore nella directory %s\n", nome_dir);
        return -1; // non riesco a leggere i metadati della directory
    }

    if(S_ISDIR(statbuf.st_mode)) {return 0;} //è una directory
    else return -1;

}

void chiudo_worker(int n_worker,int fd_skt,pthread_t *workerID,threadArgs_t *workerARGS,BQueue_t *q)
{
   
    for(int j=0;j<n_worker; ++j) {
        push(q, "finish"); //per chiudere gli worker creati prima del fallimento
	}

    for(int j=0;j<n_worker; ++j)
    {
        pthread_join(workerID[j], NULL);
    }

    deleteBQueue(q, NULL);
    close(fd_skt);
    free(workerID);
    free(workerARGS);
    
}



int naviga_directory(const char* nome_dir, BQueue_t *q, float delay,volatile sig_atomic_t* stopflag,int n_worker,int fd_skt,pthread_t *workerID,threadArgs_t *workerARGS,int pid,volatile sig_atomic_t* stampaflag) //da qui metto direttamente i file trovati in coda
{  
    DIR* dir;
    if((dir=opendir(nome_dir)) == NULL){
        perror("opendir");
        fprintf(stderr,"errore aprendo la directory%s\n",nome_dir);
        return -1;
    }
    else
    {
        struct dirent* infile; //struttura che descrive il file nella directory viene ritornata da readdir()
        errno=0;
        while((*stopflag)==0  && (infile=readdir(dir))!= NULL && ((errno==0 || errno==60))){ 
            //errno==60 é operation timed out, ma io con la sleep voglio aspettare 
            if(strcmp(infile->d_name, ".") != 0 && strcmp(infile->d_name, "..") != 0 && strncmp(infile->d_name,".",1)) //lultima opzione è per non leggere .DS_Store (on Mac)
            {
                char filenamepath [MAX_LEN_FILE_NAME]; //path del file letto con readdir (file "infile")
	            int len1 = strlen(nome_dir);
	            int len2 = strlen(infile->d_name);
	            if ((len1+len2+2)>MAX_LEN_FILE_NAME) //+2 perche ci devo aggiungere / e /0
                {
		            fprintf(stderr, "ERRORE: MAXFILENAME troppo piccolo\n");
                    closedir(dir);
		            return -1;
	            }

                strcpy(filenamepath,nome_dir);
	            strcat(filenamepath,"/");
	            strcat(filenamepath,infile->d_name);

                if(controllo_file_regolare(filenamepath)==0) //è un file
                {
                    //FUNZIONE CHE METTE IN CODA !
                    //è un file regolare, devo metterlo in coda, con però il path! quindi devo passare filenamepath
                    // filename path ora contiene il percorso al file regolare da mettere in coda 
                    if(inserisci(filenamepath,q)!=0){

                        fprintf(stderr, "ERRORE: inserimento in lista fallito\n");
                        closedir(dir);
		                return -1;
                    }  
                    sleep(delay);
                }
                else
                {
                    if((*stopflag)==0)
                    {
                        //potrebbe essere una sotto directory!
                        if (controllo_directory(filenamepath)==0) //significa che il file aperto con readdir è una directory
                        {
                            //navigo ricorsivamente la directory 
                            if(naviga_directory(filenamepath,q,delay,stopflag,n_worker,fd_skt,workerID,workerARGS,pid,stampaflag)==-1)
                            {
                                fprintf(stderr, " errore in naviga directory %s\n", nome_dir);
                                chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);
                                closedir(dir);
                                return -1;
                            }
                        }
                        else
                        {
                            fprintf(stderr, "potrei aver avuto errore in controllo_file_regolare o %s non è né una directory né un file regolare)\n",filenamepath);
                            closedir(dir);
                            return -1;
                        }
                    }
                    
                }
  
            } 
            
        }
        if (errno!= 0 && errno!= 60)
        {
            if(stampaflag==0) // perchè il segnale che setta stampaflag (sigusr1) puo essere sentito al ritorno di readdir però non devo uscire, devo continuare a navigare directory
            {
                perror("readdir\n");
                closedir(dir);
                return -1;
            }
            
        } 
        closedir(dir);
    }
    return 0;
}


int thread_master(int argc, char* argv[],char *socket_name,volatile sig_atomic_t* stopflag,volatile sig_atomic_t* stampaflag,int pid)
{
    int option;
    int n_worker=DEFAULT_N_WORKER;
    int len_queue=DEFAULT_LEN_QUEUE;
    float delay=DEFAULT_DELAY;
    int setn=0;
    int setq=0;
    float sett=0;
    char* nome_dir=NULL;

    while ((option = getopt(argc, argv, "n:q:d:t:")) != -1 ) {
        switch (option) {
            case 'n': {
                setn=(int) isNumber(optarg);
                break;
            }
            case 'q': {
                setq=(int) isNumber(optarg);
                break;
            }
            case 'd': { 
                nome_dir=optarg;
                break;
            }
            case 't': {
                sett=(float) isNumber(optarg);
                break;
            }
            case '?': {
                break;
            }
            default: {
                break;
            }
                
        }
    }

    if(setn<=0)//significa che largomento passato non è un numero
    {
        fprintf(stdout,"L'opzione -n richiede un numero intero positivo, verrà usato quello di default: n worker=%d\n",n_worker);
    }
    else
    {
        n_worker=setn;
        fprintf(stdout,"n worker=%d\n",n_worker);
    }

    if(setq<=0)//significa che largomento passato non è un numero
    {
        fprintf(stdout,"L'opzione -q richiede un numero intero positivo, verrà usato quello di default: lunghezza coda=%d\n",len_queue);
    }
    else
    {
        len_queue=setq;
        fprintf(stdout,"lunghezza coda=%d\n",len_queue);
    }

     if(sett<=0)//significa che largomento passato non è un numero
    {
        fprintf(stdout,"L'opzione -t richiede un numero intero positivo, verrà usato quello di default: delay=%f\n",delay);
    }
    else
    {
        delay=sett/1000; //millesecondi
        fprintf(stdout,"delay=%f\n",delay);
    }

    //creo socket lato client

    int fd_skt=0; 
    struct sockaddr_un sa;
    strcpy(sa.sun_path, socket_name);
    sa.sun_family = AF_UNIX;

    if((fd_skt = socket(AF_UNIX, SOCK_STREAM, 0)) == -1){ 
        perror("socket");
        return -1;
    }

    while(connect(fd_skt, (struct sockaddr *) &sa, sizeof(sa))==-1){
        
        if(errno==ENOENT)
        {
            fprintf(stdout,"error = enoent aspetto e riprovo a connettermi\n");
            sleep(1);
        }
        else
        {
            fprintf(stderr,"errore nella connect\n");
            perror("connect");
            close(fd_skt);
            return -1;
        }
            
    }

    // socket creato ora ci posso andare a scrivere con gli worker
    //inizio a crare gli worker 
    pthread_t    *workerID;
    threadArgs_t *workerARGS;
    workerID= malloc(n_worker*sizeof(pthread_t)); //array di id di worker

    if (!workerID) 
    {
        fprintf(stderr, "malloc fallita\n");
        close(fd_skt);
	    return -1;
    }
    workerARGS = malloc(n_worker*sizeof(threadArgs_t)); // vettore che contiene in ogni cella la struttura dati thread args

    if (!workerARGS) 
    {
        fprintf(stderr, "malloc fallita\n");
        close(fd_skt);
        free(workerID);
	    return -1;
    }

    BQueue_t *q = initBQueue(len_queue);
    if (!q) 
    {
	    fprintf(stderr, "initBQueue fallita\n");
	    perror("initBQueue");
        close(fd_skt);
        free(workerID);
        free(workerARGS);
        exit(EXIT_FAILURE);
    }

    for(int i=0;i<n_worker; ++i) {	
	    workerARGS[i].thid=i;
	    workerARGS[i].q=q;
        workerARGS[i].fd_skt=fd_skt; 
        workerARGS[i].stampaflag=stampaflag;
        
    }   

    // prima creo i thread consumatori e poi inizio a inserire i dati in coda man mano che li leggo da linea di comando 

    for(int i=0;i<n_worker; ++i)
    {
        if (pthread_create(&workerID[i], NULL,Worker_consumer, &workerARGS[i]) != 0) { //ritorna il codice di errore del thread creato
	        fprintf(stderr, "pthread_create fallita (Consumer)\n");
            perror("pthread_create");             
            for(int j=0;j<i; ++j) {

                push(q, "finish"); //per chiudere gli worker creati prima del fallimento
	
	        }

            for(int j=0;j<i; ++j)
            {
                pthread_join(workerID[j], NULL);
                
            }
            deleteBQueue(q, NULL);
            close(fd_skt);
            free(workerID);
            free(workerARGS);
            return -1;
        }
    }

    if((*stopflag)==0 && nome_dir!=NULL)// è stata passata una stringa a -d, devo controllare sia una directory
    { //se stopflag è a 1 non devo piu leggere file 
        
        if (controllo_directory(nome_dir) != 0)
        {
            fprintf(stderr, "ERRORE: %s non e una directory\n", nome_dir);
            chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);
            return -1;
        }
        else// e una directory, la navigo alla ricerca di file
        {
            if(naviga_directory(nome_dir,q,delay,stopflag,n_worker,fd_skt,workerID,workerARGS,pid,stampaflag)!=0)
            {
                fprintf(stderr, " errore in naviga directory %s\n", nome_dir);
                chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);           
                exit(EXIT_FAILURE);
            }
        }
        
           
    }

    //devo verificare che siano file regolari
    while((*stopflag)==0 && argv[optind]!= NULL && (strncmp(argv[optind], "-d", 2) != 0)) // se ho messo -d dopo la lista di file esco perche sono arrivata a fine della lista di file
    {
        int len=strlen(argv[optind]);
        if(len<MAX_LEN_FILE_NAME-1) // -1 perche ci devo poi mettere \0
        {
            char *filename = argv[optind]; //lo faccio puntare a argv[optind]
            if (controllo_file_regolare(filename) != 0){
                fprintf(stderr, "%s non e un file regolare\n", filename);
                chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);
                return -1;
            }
            else// è un file regolare lo devo mettere in coda
            {
                if(inserisci(filename,q)!=0){
                    fprintf(stderr, "ERRORE: inserimento in lista fallito\n");
                    chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);
                    return -1;
                }

                sleep(delay);
            }
        }
        else
        {
            fprintf(stderr, "ERRORE il nome del file è troppo lungo\n");
            chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);
            return -1;
        }
	    optind=optind+1;                
    }

    if((*stopflag)==0 && argv[optind] != NULL && strcmp(argv[optind], "-d") == 0) //controllo se ho messo lopzione -d dopo la lista dei file (in quanto se e presente dopo la lista dei file getopt non la ha presa)
    {
        nome_dir=argv[optind+1]; //+1 perche vado a vedere chi ce dopo -d
        if(controllo_directory(nome_dir) != 0){
            fprintf(stderr, "%s non e una directory\n", nome_dir);
            chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);
            return -1;
            
        }
        else// è una directory, la navigo alla ricerca di file
        {
            if(naviga_directory(nome_dir,q,delay,stopflag,n_worker,fd_skt,workerID,workerARGS,pid,stampaflag)!=0)
            {
                fprintf(stderr, "%s non é una DIRECTORY\n", nome_dir);
                chiudo_worker(n_worker,fd_skt,workerID,workerARGS,q);
                return -1;
            }
        }

    }

    if(nome_dir==NULL)//significa che non è stato passato nessun arg a -d
    {
        fprintf(stdout,"L'opzione -d non ha parametri\n"); 
    }

    //ora devo attendere che lunico produttore termini l'insersione dei nomi file in coda
    // produco tanti EOS="finish" quanti sono i consumatori
        
    for(int i=0;i<n_worker; ++i) {
        if (push(q, "finish") == -1) {
	        fprintf(stderr, "Errore: push\n");//"EOS" che fa terminare i thread worker
            deleteBQueue(q, NULL);
            close(fd_skt);
            free(workerID);
            free(workerARGS);
            return -1;
	    }
  
	}

    //da qui gli worker non esistono piu, finche non faccio la join però rimangobo zombie
    
    for(int i=0;i<n_worker; ++i)
    {
        if ((pthread_join(workerID[i],NULL)!= 0)) {
            fprintf(stderr, "Error pthread_join \n");
            deleteBQueue(q, NULL);
            close(fd_skt);
            free(workerID);
            free(workerARGS);
            return -1;
        }
    }

    deleteBQueue(q, NULL);
    free(workerID);
    free(workerARGS);

    //scrivo eof che quando viene letto da collector chiude il socket e il collector stampa la lista
    char *finish="finish";
    int length_data=strlen("finish")+1;
    if (writen(fd_skt, &length_data, sizeof(int))==-1) {// scrivo la lunghezza (con il carattere terminatore /0 )della stringa finish
        fprintf(stderr, "Error writen \n");
        close(fd_skt);
        return -1;
    }
    if (writen(fd_skt,finish,sizeof(char)*(length_data))==-1) {//scrivo finish con il terminatore /0
        fprintf(stderr, "Error writen \n");
        close(fd_skt);
        return -1;
    }
    
    close(fd_skt);
   
    return 0;   

}
