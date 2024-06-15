
public class Utente{ 
    private String nomeutente;
    private String password;
    private boolean logged;
    private int Npartite_giocate;
    private float percentuale_vinte; // giocate/vinte
    private int streak;
    private int max_streak;
    private int[] guess_distribution;
    private float punteggio;
    
    public Utente(String nomeutente, String password) throws IllegalArgumentException, NullPointerException {
		// controllo che nessuno dei due parametri sia null
		if (nomeutente == null || password == null)
			throw new NullPointerException();

		// controllo che la password non sia la stringa vuota
		if (password.equals(""))// altrimenti sollevo un'eccezione
			throw new IllegalArgumentException("La stringa vuota non è una password valida");

		// username e password sono quelli passati al costruttore
		this.nomeutente = nomeutente;
		this.password = password;
        this.logged=false;
        this.Npartite_giocate=0;
        this.percentuale_vinte=0;
        this.streak=0;
        this.max_streak=0;
        this.guess_distribution=new int[12];
        this.punteggio=0;

        // inizializzo il vettore guess_distribution a 0
		for (int i = 0; i < 12; i++) {
			guess_distribution[i] = 0;
		}
        
    }

    // non è synchronized perche il nome utente rimane invariato, non puo essere modificato
    public String getNomeutente (){
        return this.nomeutente;
    }

    // non è synchronized perche la password rimane invariata, non puo essere modificato
    public String getpassword (){
        return this.password;
    }

    public synchronized float get_punteggio (){
        return this.punteggio;
    }


    //non è synchronized perchè chiamo questo metodo solo all'interno di blocchi synchronized su l'utente a cui applico il metodo
    public boolean getlogged()
    {
        return this.logged;
    }

    //non è synchronized perchè effettuo questa op quando ho la lock sull utente grazie all'utilizzo di blocchi synchronized, vedi registro_registarzioni_impl    
    public void setlogged() 
    {
        this.logged=true;
    }

    public synchronized void setlogout()
    {
        this.logged=false;
    }

    public synchronized void stampa_dati_utente (){
        System.out.println("nomeutente: " +this.nomeutente);
       // System.out.println("password: " +this.password);
        System.out.println("logged: " +this.logged);
        System.out.println("N° partite giocate: " +this.Npartite_giocate);
        System.out.println("Percentuale vinte: " +this.percentuale_vinte);
        System.out.println("Streak: " +this.streak);
        System.out.println("Max streak: " +this.max_streak);
        System.out.print("Guess distribution: [");
        for(int i=0;i<12;i++)
        {
            System.out.print(this.guess_distribution[i]+" ");
        }
        System.out.println("]");
        System.out.println("Punteggio: " +this.punteggio);
    }

    public synchronized void incrementa_partite_giocate (){
        this.Npartite_giocate=this.Npartite_giocate+1;
    }

    public synchronized void aggiungi_streak() //da chimare ogni volta che lutente vince una partita
    {
        this.streak=this.streak+1;
    }

    public synchronized void aggiorna_guess_distribution(int n_tentativi)
    {
        guess_distribution[n_tentativi-1]=guess_distribution[n_tentativi-1]+1; //perche gli array iniziano da 0
    }

    public synchronized void reset_streak() //da chiamare ogni volta che lutente perde una partita
    {
        //aggiorno eventualmente max_streack
        if(streak>max_streak)
        {
            max_streak=streak;
        }
        streak=0;
        
    }

    public synchronized void aggiorna_percentuale_vinte(){
        int partite_vinte=0;
        for(int i=0;i<12;i++)
        {
            partite_vinte=partite_vinte+guess_distribution[i];
        }
        this.percentuale_vinte=(partite_vinte*100)/Npartite_giocate;
    }

    public synchronized float aggiorna_punteggio()
    {
        int partite_vinte=0;
        for(int i=0;i<12;i++)
        {
            partite_vinte=partite_vinte+guess_distribution[i];
        }
        float tentativi_medi=0;
        int somma=0;
        for(int i=0;i<12;i++)
        {
            somma=somma+((i+1)*guess_distribution[i]); //somma di tutti i temtativi nelle partite vinte
        }
        tentativi_medi=somma/partite_vinte;
        punteggio=partite_vinte*(1/tentativi_medi);
        return punteggio;

    }

    public synchronized String get_stat_as_string(){
        String statistiche="N partite giocate: "+Npartite_giocate+"\n"
                            +"% partite vinte: "+percentuale_vinte+"%"+"\n"
                            +"Streak: "+streak+"\n"
                            +"MAX Streak: "+max_streak+"\n"
                            +"Guess Distribution: "+"\n"
                            +"1 tentativo: "+guess_distribution[0]+"\n"
                            +"2 tentativi: "+guess_distribution[1]+"\n"
                            +"3 tentativi: "+guess_distribution[2]+"\n"
                            +"4 tentativi: "+guess_distribution[3]+"\n"
                            +"5 tentativi: "+guess_distribution[4]+"\n"
                            +"6 tentativi: "+guess_distribution[5]+"\n"
                            +"7 tentativi: "+guess_distribution[6]+"\n"
                            +"8 tentativi: "+guess_distribution[7]+"\n"
                            +"9 tentativi: "+guess_distribution[8]+"\n"
                            +"10 tentativi: "+guess_distribution[9]+"\n"
                            +"11 tentativi: "+guess_distribution[10]+"\n"
                            +"12 tentativi: "+guess_distribution[11]+"\n"
                            +"Punteggio: "+punteggio+"\n";
        
        return statistiche;                
    }
    
}
