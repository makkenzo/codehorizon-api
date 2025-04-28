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

        val authorities = user.roles.map { role ->
            val authorityName = if (role.startsWith("ROLE_")) role else "ROLE_$role"
            SimpleGrantedAuthority(authorityName)
        }.toList()

        return User(user.email, user.passwordHash, user.isVerified, true, true, true, authorities)
    }
}