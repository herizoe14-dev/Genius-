package com.genius.echos;

/** Services propres à la plateforme (Android : verrous Wi-Fi pour la diffusion locale). */
public interface Platform {
    /** Appelé quand on commence à utiliser le réseau local (héberger / rechercher). */
    void lanStart();
    /** Appelé quand on n'en a plus besoin. */
    void lanStop();

    Platform NONE = new Platform() {
        @Override public void lanStart() {}
        @Override public void lanStop() {}
    };
}
