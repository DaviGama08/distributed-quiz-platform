package pt.isec.server.model.question;

import java.io.Serializable;
import java.util.Objects;

public final class Option implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer id;
    private OptionLetter letter;
    private String text;

    public Option(){}

    public Option(Integer id, OptionLetter letter, String text) {
        validate(id, letter, text);
        this.id     = id;
        this.letter = letter;
        this.text   = text;
    }

    public Option(OptionLetter letter, String text) {
        this(null, letter, text);
    }

    //gets/sets
    public void setId(Integer id) {
        if (id != null && id <= 0)
            throw new IllegalArgumentException("id must be positive if provided");
        this.id = id;
    }
    public void setLetter(OptionLetter letter) {
        if (letter == null)
            throw new IllegalArgumentException("letter cannot be null");
        this.letter = letter;
    }
    public void setText(String text) {
        if (text == null || text.isBlank())
            throw new IllegalArgumentException("text cannot be null or blank");
        this.text = text;
    }

    public Integer getId() {return id;}
    public OptionLetter getLetter() {return letter;}
    public String getText() {return text;}

    //toString
    @Override
    public String toString(){
        return "Option{id=" + id + ", letter=" + letter + ", text='" + text + "'}";
    }

    //equals/hashCode
    @Override
    public boolean equals(Object o){
        if(o == this) return true;
        if(o == null || o.getClass() != this.getClass())return false;
        Option _o = (Option)o;

        return Objects.equals(_o.id, id)&&
                Objects.equals(_o.letter, letter)&&
                Objects.equals(_o.text, text);
    }
    @Override
    public int hashCode(){return Objects.hash(id, letter, text);}

    private static void validate(Integer id, OptionLetter letter, String text) {
        if (id != null && id <= 0)
            throw new IllegalArgumentException("id must be positive if provided");

        if (letter == null)
            throw new IllegalArgumentException("letter cannot be null");

        if (text == null || text.isBlank())
            throw new IllegalArgumentException("text cannot be null or blank");
    }
}
