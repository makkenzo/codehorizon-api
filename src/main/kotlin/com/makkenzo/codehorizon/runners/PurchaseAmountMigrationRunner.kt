//package com.makkenzo.codehorizon.runners
//
//import com.makkenzo.codehorizon.models.Purchase
//import com.makkenzo.codehorizon.repositories.CourseRepository
//import org.springframework.boot.CommandLineRunner
//import org.springframework.data.mongodb.core.MongoTemplate
//import org.springframework.data.mongodb.core.query.Criteria
//import org.springframework.data.mongodb.core.query.Query
//import org.springframework.data.mongodb.core.query.Update
//import org.springframework.stereotype.Component
//
//@Component
//class PurchaseAmountMigrationRunner(
//    private val mongoTemplate: MongoTemplate,
//    private val courseRepository: CourseRepository
//) : CommandLineRunner {
//
//    override fun run(vararg args: String?) {
//        println("--- Запуск миграции amount/currency для покупок ---")
//        val query = Query(Criteria.where("amount").exists(false))
//        val purchasesToMigrate = mongoTemplate.find(query, Purchase::class.java)
//        var updatedCount = 0L
//
//        if (purchasesToMigrate.isEmpty()) {
//            println("Не найдено покупок для миграции.")
//            return
//        }
//
//        println("Найдено покупок для миграции: ${purchasesToMigrate.size}")
//
//        val courseIds = purchasesToMigrate.map { it.courseId }.distinct()
//        val coursesMap = courseRepository.findAllById(courseIds).associateBy { it.id }
//
//        purchasesToMigrate.forEach { purchase ->
//            val course = coursesMap[purchase.courseId]
//            if (course != null) {
//                val priceInCents = ((course.price - (course.discount ?: 0.0)) * 100).toLong().coerceAtLeast(0L)
//                val currencyCode = "usd"
//
//                val updateQuery = Query(Criteria.where("_id").`is`(purchase.id))
//                val update = Update()
//                    .set("amount", priceInCents)
//                    .set("currency", currencyCode)
//
//                val result = mongoTemplate.updateFirst(updateQuery, update, Purchase::class.java)
//                if (result.modifiedCount > 0) {
//                    updatedCount++
//                } else {
//                    println("!!! Не удалось обновить покупку ${purchase.id}")
//                }
//            } else {
//                println("!!! Не найден курс ${purchase.courseId} для покупки ${purchase.id}. Пропускаем.")
//            }
//        }
//
//        println("Миграция завершена. Обновлено покупок: $updatedCount")
//        println("---------------------------------------------------")
//    }
//}