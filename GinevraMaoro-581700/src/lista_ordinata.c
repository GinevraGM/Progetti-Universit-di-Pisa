#include <dirent.h>
#include <sys/stat.h>
#include <unistd.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>

#include <lista_ordinata.h>



void stampa_lista(elem *head)
{
    elem *temp=head;
    if(temp!=NULL) //per il caso in cui mi stoppo prima che sia stato messo in lista qualsisi elemento
    {
        while (temp->next!=NULL) {
            fprintf(stdout,"%ld %s \n",temp->result,temp->path);
            temp=temp->next;
        }
        fprintf(stdout,"%ld %s\n",temp->result,temp->path);
    }
    
}

void libera_lista(elem *head)
{
    elem *prec=NULL;
    while (head!=NULL) {
        prec=head;
        head=head->next;
        free(prec->path);
        free(prec);
    }
}

elem* inserisci_lista_ordinata(long result, char *data,int lenght_data, elem *head)
{
    
    elem *nuovo=(elem*)malloc(sizeof(elem));
    nuovo->next=NULL;
    nuovo->prev=NULL;
    nuovo->result=result;
    char *pathname=malloc(sizeof(char)*lenght_data);

    if (pathname==NULL) {
	    perror("Producer malloc");
	    return NULL;
	}

    strcpy(pathname,data);
    nuovo->path=pathname;

    if(head==NULL)
    {   
        head=nuovo;
        return head;
    }
    else
    {
        elem *corr=head;
        elem *prec=NULL;

        while(corr!=NULL && nuovo->result>corr->result)
        {
            prec=corr;
            corr=corr->next;
        }

        if(corr==NULL)
        {
            prec->next=nuovo;
            nuovo->prev=prec;
            return head;
        }

        if(corr->prev==NULL)//devo metterlo prima della testa 
        {
            nuovo->next=corr;
            corr->prev=nuovo;
            return nuovo; //nuova testa
        }
        else
        {
            nuovo->next=corr;
            nuovo->prev=prec;
            prec->next=nuovo;
            corr->prev=nuovo;
            return head;
        }
    }

    return head;
}

