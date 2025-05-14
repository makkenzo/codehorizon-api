package com.makkenzo.codehorizon.services


import com.makkenzo.codehorizon.repositories.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(private val userRepository: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByEmail(username)
            ?: throw UsernameNotFoundException("User not found with email: $username")

        val authorities = mutableListOf<SimpleGrantedAuthority>()

        user.roles.forEach { role ->
            authorities.add(SimpleGrantedAuthority(if (role.startsWith("ROLE_")) role else "ROLE_$role"))
        }

        authorities.addAll(getPermissionsForRoles(user.roles))

        return User(
            user.email,
            user.passwordHash,
            user.isVerified,
            true,
            true,
            true,
            authorities.distinct()
        )
    }

    private fun getPermissionsForRoles(roles: List<String>): List<SimpleGrantedAuthority> {
        val finalPermissions = mutableSetOf<String>()

        val userPermissions = setOf(
            "user:read:self",
            "user:edit:self",
            "course:read:public_list",
            "course:read:public_details",
            "course:read:enrolled_content",
            "course:enroll:free",
            "course:purchase",
            "lesson:complete",
            "review:create",
            "review:edit:own",
            "review:delete:own",
            "mentorship_application:apply",
            "mentorship_application:read:self",
            "certificate:read:self",
            "certificate:download:self",
            "file:upload:avatar",
            "wishlist:add:self",
            "wishlist:remove:self",
            "wishlist:read:self",
            "course:read:list:self_enrolled"
        )

        val mentorSpecificPermissions = setOf(
            "course:create",
            "course:edit:own",
            "course:delete:own",
            "course:view_students:own",
            "lesson:add:own_course",
            "lesson:edit:own_course",
            "lesson:delete:own_course",
            "file:upload:course_preview",
            "file:upload:lesson_attachment",
            "file:upload:signature",
            "course:read:list:own_created",
            "course:read:details:own_created"
        )

        val adminSpecificPermissions = setOf(
            "user:admin:read:any",
            "user:admin:edit:any",
            "user:admin:edit_roles",
            "course:edit:any",
            "course:delete:any",
            "course:view_students:any",
            "lesson:add:any_course",
            "lesson:edit:any_course",
            "lesson:delete:any_course",
            "mentorship_application:admin:read:any",
            "mentorship_application:admin:approve",
            "mentorship_application:admin:reject",
            "admin_dashboard:view",
            "course:read:list:all",
            "course:read:details:any",
            "file:upload:other",
            "achievement:admin:manage",
            "admin:job:run"
        )

        if (roles.any { it.equals("ROLE_USER", ignoreCase = true) || it.equals("USER", ignoreCase = true) }) {
            finalPermissions.addAll(userPermissions)
        }

        if (roles.any { it.equals("ROLE_MENTOR", ignoreCase = true) || it.equals("MENTOR", ignoreCase = true) }) {
            finalPermissions.addAll(userPermissions)
            finalPermissions.addAll(mentorSpecificPermissions)
        }

        if (roles.any { it.equals("ROLE_ADMIN", ignoreCase = true) || it.equals("ADMIN", ignoreCase = true) }) {
            finalPermissions.addAll(userPermissions)
            finalPermissions.addAll(mentorSpecificPermissions)
            finalPermissions.addAll(adminSpecificPermissions)
        }


        return finalPermissions.map { SimpleGrantedAuthority(it) }
    }
}