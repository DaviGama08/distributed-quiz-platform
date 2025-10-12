package pt.isec.common.model.question;

import pt.isec.common.model.common.OptionLetter;

import java.util.Objects;

public final class Option {
    private Integer id;
    private OptionLetter letter;
    private String text;

    //gets/sets
    public Integer getId() {return id;}
    public void setId(Integer id) {this.id = id;}
    public OptionLetter getLetter() {return letter;}
    public void setLetter(OptionLetter letter) {this.letter = letter;}
    public String getText() {return text;}
    public void setText(String text) {this.text = text;}

    //equals/hashCode
    public boolean equals(Object o){
        if(o == this) return true;
        if(o == null || o.getClass() != this.getClass())return false;
        Option _o = (Option)o;
        return Objects.equals(_o.id, id)&&
                Objects.equals(_o.letter, letter)&&
                Objects.equals(_o.text, text);
    }
    public int hashCode(){return Objects.hash(id, letter, text);}
}
