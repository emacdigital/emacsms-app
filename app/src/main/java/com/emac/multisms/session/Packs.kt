package com.emac.multisms.session

/**
 * Les offres d'achat de crédits affichées dans l'app.
 *
 * - `id` doit correspondre au produit Chariow ET à PRODUCT_CREDITS dans le backend.
 * - `credits` est indicatif (le montant réel est crédité par le backend via le webhook).
 * - `url` = le lien de paiement Chariow de ce pack (Mobile Money).
 *
 * Modifie librement les montants et les liens selon tes offres.
 */
data class CreditPack(
    val id: String,
    val priceLabel: String,
    val credits: Int,
    val url: String
)

object Packs {
    val all = listOf(
        CreditPack("pack-1000", "1 000 FCFA", 1000, "https://emacdigital.com/prd_j4c9nciv"),
        CreditPack("pack-5000", "5 000 FCFA", 6000, "https://emacdigital.com/prd_2ln7q397"),
        CreditPack("pack-10000", "10 000 FCFA", 15000, "https://emacdigital.com/prd_jkq4lerx")
    )
}
