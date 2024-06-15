import java.util.HashSet;
import java.util.Set;

public class SecretWordObject {
    private String secretword;
    private Set<String> giocatori; //set di giocatori che stanno giocando quella parola
    private String traduzione;
    private int n_secretword=0;

    public SecretWordObject(){
        this.giocatori= new HashSet<>();
    }


    //i metodi non sono syncronized perche nel server accedo a SWO sempre in blocchi syncronuized, 
    //poiche quando opero con la SWO devo farci sempre piu di una operazione, quindi mi conviene mettere tutte le op in un blocco syncronized

    // setto la nuova secretword
    public void setSW(String parola) throws NullPointerException{
        if(parola==null) throw new NullPointerException();
        this.secretword=parola;
    }

    // setto il numero per la sw
    public void set_n_SW(int n_secretword) {
        
        this.n_secretword=n_secretword;
    }

    // setto la traduzione
    public void set_traduzione(String traduzione) throws NullPointerException{
        if(traduzione==null) throw new NullPointerException();
        this.traduzione=traduzione;
    }

    //resetto il set di giocatori che stanno giocando quella parola
    public void resetgiocatori(){
        giocatori.clear();
    }

    public void addgiocatore(String username)throws NullPointerException
    {
        if(username==null) throw new NullPointerException();
        giocatori.add(username);
        
    }

    public boolean containsusername(String username)throws NullPointerException
    {
        if(username==null) throw new NullPointerException();
        return giocatori.contains(username);
    }

    public String getSW(){ 
    
        return this.secretword;
    
    }

    public String get_traduzione(){
        
        return this.traduzione;
    
    }

    public int get_n_secretword(){

        return this.n_secretword;
    }
    
}
