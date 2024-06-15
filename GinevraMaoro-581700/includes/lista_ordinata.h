#if !defined(LISTA_ORDINATA_H)
#define LISTA_ORDINATA_H



typedef struct _elem{
    struct _elem *prev;
    struct _elem *next;
    long result;
    char *path;    
} elem;

elem* inserisci_lista_ordinata(long result, char *data,int lenght_data, elem *head);

void stampa_lista(elem *head);

void libera_lista(elem *head);


#endif /* LISTA_ORDINATA_H */
