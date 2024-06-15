import java.util.LinkedList;

public class Partita { //la classe partita non importa sia sincronizzata perche ci accedera solo lutente loggato che sta giocando 
    private int tentativi;
    private LinkedList <String> suggerimenti;
    private String secretword;
    private int n_secretword;
    private String traduzione;
    private int flag; //flag che mi dice se questa partita è stata usata per aggiornar le statistiche 
    //0: è gia stata usta per aggiornare le statistiche 
    //1:non è stata usata per aggiornare le statistiche 

    public Partita(String secretword,String traduzione,int n_secretword) throws NullPointerException,IllegalArgumentException  
    {
        if(secretword==null||traduzione==null)throw new NullPointerException();
        if(n_secretword==-1)throw new IllegalArgumentException();
        this.tentativi=0;
        this.suggerimenti=new LinkedList<String>();
        this.secretword=secretword;
        this.n_secretword=n_secretword;
        this.traduzione=traduzione;
        this.flag=1;
    }

    public String getsecretword(){
        return this.secretword;
    }

    public int get_n_secretword(){
        return this.n_secretword;
    }

    public String get_traduzione(){
        return this.traduzione;
    }

    public int gettentativi(){
        return this.tentativi;
    }

    public void add_tentativo()
    {
        this.tentativi=this.tentativi+1;
    }

    public void addSuggerimento(String suggerimento) throws NullPointerException//suggerimento è una stringa fatta di X,+,?
    {
        if(suggerimento==null||suggerimenti==null)throw new NullPointerException();
        suggerimenti.add(suggerimento);

    }

    public void set_flag()
    {
        this.flag=0;
    }

    public int get_flag()
    {
        return this.flag;
    }

    public void stampa_partita (){

        System.out.println("tentativi: " +this.tentativi);
        System.out.println("secretword n° "+this.n_secretword+" "+this.secretword);
        System.out.println("Suggerimenti");
        for(String s:suggerimenti)
        {
            System.out.println(s);
        }
        System.out.println("flag: " +this.flag);
    }

    public String get_partita_as_string(){
        String c="Wordle "+this.n_secretword+" "+this.tentativi+ "/12\n";
        for(int i=0;i<suggerimenti.size();i++)
        {
            c=c+suggerimenti.get(i)+"\n";
            
        }
        return c;
                       
    }
    
}