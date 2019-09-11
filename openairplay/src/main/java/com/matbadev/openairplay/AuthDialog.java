package com.matbadev.openairplay;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import java.awt.Window;

class AuthDialog implements Auth {

    private Window parent;

    public AuthDialog(Window parent) {
        this.parent = parent;
    }

    public String getPassword(String hostname, String name) {
        final JPasswordField password = new JPasswordField();
        JOptionPane optionPane = new JOptionPane(password, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = optionPane.createDialog(parent, "Password:");
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        int result = (Integer) optionPane.getValue();
        dialog.dispose();
        if (result == JOptionPane.OK_OPTION) {
            return new String(password.getPassword());
        }
        return null;
    }

}
