
import java.util.Objects;

@SuppressWarnings("overrides")

public class entry_classifica implements Comparable <entry_classifica>{

    private String nomeutente;
    private float punteggio;

    //costruttore con punteggio a 0
    public entry_classifica(String nomeutente){
        this.nomeutente=nomeutente;
        this.punteggio=0;
    }
    //costruttore con punteggio
    public entry_classifica(String nomeutente,float punteggio){
        this.nomeutente=nomeutente;
        this.punteggio=punteggio;
    }

    public float get_punteggio()
    {
        return this.punteggio;
    }

    public String get_nomeutente()
    {
        return this.nomeutente;
    }

    //@Override: del metodo equals della classe entry_classifica
    public boolean equals(Object obj) {
        if (this == obj) {
            return true; // Sono lo stesso oggetto
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false; // L'oggetto passato è null o non è della stessa classe
        }
        entry_classifica other = (entry_classifica) obj;

        return Objects.equals(nomeutente, other.nomeutente);
    }

    //@Override: del metodo compareTo della classe entry_classifica
    public int compareTo(entry_classifica ec) {
		
        return Double.compare((double)this.punteggio, (double)ec.get_punteggio());

	}
    
}
