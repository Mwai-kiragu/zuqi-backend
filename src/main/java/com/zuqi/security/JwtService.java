package com.zuqi.security;

import com.zuqi.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(new HashMap<>(), userDetails);
    }

    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, accessTokenExpiration);
    }

    /** Preferred method — embeds userId, merchantId, distributorId, customerId, roles in token. */
    public String generateUserAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        if (user.getMerchantId() != null) claims.put("merchantId", user.getMerchantId().toString());
        if (user.getDistributorId() != null) claims.put("distributorId", user.getDistributorId().toString());
        if (user.getCustomerId() != null) claims.put("customerId", user.getCustomerId().toString());
        if (user.getRoles() != null) {
            claims.put("roles", user.getRoles().stream().map(r -> r.getName()).toList());
        }
        return buildToken(claims, user, accessTokenExpiration);
    }

    public UUID extractUserId(String token) {
        String val = extractClaim(token, c -> c.get("userId", String.class));
        return val != null ? UUID.fromString(val) : null;
    }

    public UUID extractMerchantId(String token) {
        String val = extractClaim(token, c -> c.get("merchantId", String.class));
        return val != null ? UUID.fromString(val) : null;
    }

    public UUID extractDistributorId(String token) {
        String val = extractClaim(token, c -> c.get("distributorId", String.class));
        return val != null ? UUID.fromString(val) : null;
    }

    public UUID extractCustomerId(String token) {
        String val = extractClaim(token, c -> c.get("customerId", String.class));
        return val != null ? UUID.fromString(val) : null;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, c -> (List<String>) c.get("roles"));
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, refreshTokenExpiration);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public String generateTokenWithBranch(UserDetails userDetails, UUID branchId, boolean isHeadquarters) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("branchId", branchId.toString());
        extraClaims.put("isHq", isHeadquarters);
        if (userDetails instanceof User user) {
            extraClaims.put("userId", user.getId().toString());
            if (user.getMerchantId() != null) extraClaims.put("merchantId", user.getMerchantId().toString());
            if (user.getDistributorId() != null) extraClaims.put("distributorId", user.getDistributorId().toString());
            if (user.getCustomerId() != null) extraClaims.put("customerId", user.getCustomerId().toString());
            if (user.getRoles() != null) extraClaims.put("roles", user.getRoles().stream().map(r -> r.getName()).toList());
        }
        return buildToken(extraClaims, userDetails, accessTokenExpiration);
    }

    public UUID extractBranchId(String token) {
        String branchIdStr = extractClaim(token, claims -> claims.get("branchId", String.class));
        return branchIdStr != null ? UUID.fromString(branchIdStr) : null;
    }

    public boolean extractIsHeadquarters(String token) {
        Boolean isHq = extractClaim(token, claims -> claims.get("isHq", Boolean.class));
        return Boolean.TRUE.equals(isHq);
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secretKey.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
